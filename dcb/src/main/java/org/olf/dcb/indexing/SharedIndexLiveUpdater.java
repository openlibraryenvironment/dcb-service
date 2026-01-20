package org.olf.dcb.indexing;

import java.time.Instant;
import java.util.UUID;

import org.olf.dcb.core.clustering.RecordClusteringService;
import org.olf.dcb.indexing.job.IndexSynch;
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
import reactor.core.publisher.Mono;

@Singleton
@ExecuteOn(TaskExecutors.BLOCKING)
@Requires(bean = SharedIndexService.class)
public class SharedIndexLiveUpdater implements ApplicationEventListener<StartupEvent> {
	
	public static  enum ReindexOp {
		START
	}
	
	
	private final SharedIndexService sharedIndexService;
	private final RecordClusteringService clusters;
	private final IndexSynch indexJob;
	
	private static final Logger log = LoggerFactory.getLogger(SharedIndexLiveUpdater.class);
	
	public SharedIndexLiveUpdater(SharedIndexService sharedIndexService, RecordClusteringService recordClusteringService, R2dbcOperations r2dbcOperations, IndexSynch indexJob) {
		this.sharedIndexService = sharedIndexService;
		this.clusters = recordClusteringService;
		this.indexJob = indexJob;
	}

	@Override
	public void onApplicationEvent(StartupEvent event) {
		log.debug("Initializing shared index service...");
		
		// If this throws it will fail application startup.
		// To make this more passive, we should try/catch. 
		Mono.from(sharedIndexService.initialize()).block();
	}
	
	@Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
	protected Mono<Page<UUID>> getNextPage(@NonNull Instant before, @NonNull Pageable page) {
		return clusters.findNextPageUpdatedBefore(before, page);
	}
	
	
//	@Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
	public Mono<Void> reindexAllClusters( @NonNull ReindexOp op ) {
		
		if (ReindexOp.START == op) {
			indexJob.tryStartJob();
		}
		
		return Mono.empty();
	}
	
}
