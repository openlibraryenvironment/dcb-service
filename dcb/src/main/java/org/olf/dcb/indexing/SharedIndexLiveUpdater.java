package org.olf.dcb.indexing;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.olf.dcb.core.model.clustering.ClusterRecord;
import org.olf.dcb.core.svc.RecordClusteringService;
import org.olf.dcb.indexing.bulk.IndexOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.context.event.StartupEvent;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.r2dbc.operations.R2dbcOperations;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.transaction.TransactionDefinition.Propagation;
import io.micronaut.transaction.annotation.Transactional;
import jakarta.inject.Singleton;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoSink;

@Requires(bean = SharedIndexService.class)
@ExecuteOn(TaskExecutors.BLOCKING)
@Singleton
public class SharedIndexLiveUpdater implements ApplicationEventListener<StartupEvent> {
	
	public static  enum ReindexOp {
		START,
		STOP
	}
	
	
	private final SharedIndexService sharedIndexService;
	private final RecordClusteringService clusters;
	private final R2dbcOperations r2dbcOperations;
	
	private static final Logger log = LoggerFactory.getLogger(SharedIndexLiveUpdater.class);
	
	public SharedIndexLiveUpdater(SharedIndexService sharedIndexService, RecordClusteringService recordClusteringService, R2dbcOperations r2dbcOperations) {
		this.sharedIndexService = sharedIndexService;
		this.clusters = recordClusteringService;
		this.r2dbcOperations = r2dbcOperations;
	}

	@Override
	public void onApplicationEvent(StartupEvent event) {
		log.debug("Initializing shared index service...");
		
		// If this throws it will fail application startup.
		// To make this more passive, we should try/catch. 
		Mono.from(sharedIndexService.initialize()).block();
	}
	
	private Disposable reindexTask = null;

	@ExecuteOn(TaskExecutors.BLOCKING)
	protected void doAndReportReindex( @NonNull MonoSink<Void> startedSignal ) {
		// Grab the current timestamp to filter out resources that have been updated since we started.
		
		final Instant start = Instant.now();
		
		reindexTask = clusters.findNext1000UpdatedBefore(start, Pageable.from(0, 1000))
			.doOnSuccess(_v -> startedSignal.success() )
			.doOnError( startedSignal::error )
			.expand(p -> {
				log.trace("Preparing next page fetch");
				Pageable nextPage = p.nextPageable();
				return Mono.from(clusters.findNext1000UpdatedBefore(start, nextPage))
					.doOnSubscribe(_s -> log.trace("Fetch next page of 1000"));
			})
			.takeWhile( p -> {
				if (p.isEmpty()) {
					log.info("No more results");
					return false;
				}
				
				return true;
			})
			.doOnNext( nextP -> {
				final var currentPage = nextP.getPageNumber();
				
				if (currentPage > 0 && currentPage % 10 == 0) {
					log.info("Processed {} items", currentPage * 1000);
				}		
				log.debug("Processing page {}", nextP.getPageNumber() + 1);
			})
			.map(Page::getContent)
			.transform(sharedIndexService::expandAndProcess)
			.limitRate(2, 1) // Fetch 2 pages, and fetch another 2 when we're at 1.
			.doFinally( _signal -> this.jobMono = null )
			.subscribe(this::logResults, err -> {
				log.error("Error in reindex job", err);
			});
	}
	
	private void logResults( final List<IndexOperation<UUID, ClusterRecord>> processed ) {
		
		if (log.isDebugEnabled()) {
			log.debug( "Sent {} records to be indexed", processed.size() );
			final Map<IndexOperation.Type, Integer> counts = new HashMap<>();
			for (var op : processed) {
				var count = counts.getOrDefault(op.type(), 0);
				count ++;
				counts.put(op.type(), count);
			}
			
			counts.forEach((key, val) -> {
				log.debug("{} {}", key.name(), val);
			});
		}
	}
	
	private Mono<Void> jobMono;

	public Mono<Void> cancelReindexJob() {
		log.debug("Cancel reindex job");
		if (reindexTask == null) {
			log.debug("Job not running NOOP");
			return Mono.empty();
		}
		synchronized (this) {
			if (reindexTask == null) {
				return Mono.empty();
			}
			try {
				reindexTask.dispose();
			} finally {
				jobMono = null;
			}
			return Mono.empty();
		}
	}
	
	@Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
	public Mono<Void> reindexAllClusters( @NonNull ReindexOp op ) {
		if (op == ReindexOp.STOP) {
			return this.cancelReindexJob();
		}
		
		if (jobMono == null) {
			synchronized (this) {
				if (jobMono == null) {
					log.debug("Begin re-index");
					jobMono = Mono.fromDirect(r2dbcOperations.withTransaction(c -> {
						
						return Mono.create(this::doAndReportReindex);
						
					})).cache();
				}
			}
		} else {
			log.debug("Job already running. NOOP");
		}
		
		return jobMono;
	}
	
}
