package org.olf.dcb.indexing;

import java.time.Instant;

import org.olf.dcb.core.model.clustering.ClusterRecord;
import org.olf.dcb.core.svc.RecordClusteringService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.context.event.StartupEvent;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.data.event.EntityEventContext;
import io.micronaut.data.event.EntityEventListener;
import io.micronaut.data.model.Pageable;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.transaction.annotation.Transactional;
import jakarta.inject.Singleton;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoSink;

@Requires(bean = SharedIndexService.class)
@ExecuteOn(TaskExecutors.BLOCKING)
@Singleton
public class SharedIndexLiveUpdater implements ApplicationEventListener<StartupEvent>, EntityEventListener<ClusterRecord> {
	
	private final SharedIndexService sharedIndexService;
	private final RecordClusteringService clusters;
	
	private static final Logger log = LoggerFactory.getLogger(SharedIndexLiveUpdater.class);
	
	public SharedIndexLiveUpdater(SharedIndexService sharedIndexService, RecordClusteringService recordClusteringService) {
		this.sharedIndexService = sharedIndexService;
		this.clusters = recordClusteringService;
	}

	@Override
	public void postPersist(@NonNull EntityEventContext<ClusterRecord> context) {
		sharedIndexService.add(context.getEntity().getId());
	}

	@Override
	public void postUpdate(@NonNull EntityEventContext<ClusterRecord> context) {
		sharedIndexService.update(context.getEntity().getId());
	}

	@Override
	public void postRemove(@NonNull EntityEventContext<ClusterRecord> context) {
		sharedIndexService.delete(context.getEntity().getId());
	}

	@Override
	public void onApplicationEvent(StartupEvent event) {
		log.debug("Initializing shared index service...");
		
		// If this throws it will fail application startup.
		// To make this more passive, we should try/catch. 
		Mono.from(sharedIndexService.initialize()).block();
	}

	@ExecuteOn(TaskExecutors.BLOCKING)
	protected void doAndReportReindex( MonoSink<Void> startedSignal ) {
	// Grab the current timestamp to filter out resources that have been updated since we started.
		
		final Instant start = Instant.now();
		
		clusters.findNext1000UpdatedBefore(start, Pageable.from(0, 1000))
			.doOnSuccess(_v -> startedSignal.success())
			.expand(p -> {
				Pageable nextPage = p.nextPageable();
				return Mono.defer( () -> {
					log.trace("Fetch next page of 1000");
					return clusters.findNext1000UpdatedBefore(start, nextPage);
				});
			})
			.limitRate(1, 0)
//			.delayElements(Duration.ofSeconds(1))
			.doOnNext( nextP -> {
				final var currentPage = nextP.getPageNumber();
				
				if (currentPage > 0 && currentPage % 10 == 0) {
					log.atInfo().log("Processed {} items", currentPage * 1000);
				}
				
				log.debug("Processing page {}", nextP.getPageNumber() + 1);
				
			})
			.takeUntil( p -> {
				if (p.isEmpty()) {
					log.info("No more results");
					return true;
				}
				
				return false;
			})
			.concatMap(Flux::fromIterable)
			.subscribe(sharedIndexService::add, err -> {
				log.error("Error reindexing clusters.");
			});
	}

	@Transactional(readOnly = true)
	public Mono<Void> reindexAllClusters() {		
		return Mono.create(this::doAndReportReindex);
	}
	
}
