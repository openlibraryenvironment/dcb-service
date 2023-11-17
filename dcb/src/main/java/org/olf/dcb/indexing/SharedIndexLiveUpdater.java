package org.olf.dcb.indexing;

import java.time.Duration;
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
	
	@Transactional(readOnly = true)
	public Mono<Void> reindexAllClusters() {
		// Grab the current timestamp to filter out resources that have been updated since we started.
		
		final Instant start = Instant.now();
		
		final var throttledQueue = clusters.findNext1000UpdatedBefore(start, Pageable.from(0, 1000))
			.expand(p -> {
				Pageable nextPage = p.nextPageable();
				return clusters.findNext1000UpdatedBefore(start, nextPage);
			})
			.delayElements(Duration.ofSeconds(2));
			
			// This publisher will emit when the process starts.
			// And propagate immediate errors for us.
			final Mono<Void> begun = throttledQueue
				.next()
				.then();
		
		throttledQueue
			.doOnNext( nextP ->  log.debug("Fetching page {}", nextP.getPageNumber() + 1) )
			.takeUntil( p -> {
				if (p.isEmpty()) {
					log.info("Finished reindex job");
					return true;
				}
				
				return false;
			})
			.flatMap(Flux::fromIterable)
			.subscribe(sharedIndexService::add, err -> {
				log.error("Error reindexing clusters.");
			});
		
		return begun;
	}
	
}
