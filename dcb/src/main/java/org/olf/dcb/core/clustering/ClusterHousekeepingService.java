package org.olf.dcb.core.clustering;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.olf.dcb.ingest.IngestService;
import org.olf.dcb.storage.ClusterRecordRepository;

import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.scheduling.annotation.Scheduled;
import io.micronaut.transaction.TransactionDefinition.Propagation;
import io.micronaut.transaction.annotation.Transactional;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.function.TupleUtils;
import services.k_int.features.FeatureFlag;
import services.k_int.federation.reactor.ReactorFederatedLockService;
import services.k_int.micronaut.scheduling.processor.AppTask;

@Slf4j
@Singleton
@ExecuteOn(TaskExecutors.BLOCKING)
@FeatureFlag(ImprovedRecordClusteringService.FEATURE_IMPROVED_CLUSTERING)
public class ClusterHousekeepingService {

	private static final int BATCH_SIZE = 5000;
	private final ClusterRecordRepository clusterRecordRepo;
	private final RecordClusteringService recordClusteringService;
	private final LinkedHashSet<String> priorityReprocessingQueue = new LinkedHashSet<>();
  private final ReactorFederatedLockService lockService;
	
	public ClusterHousekeepingService(ClusterRecordRepository clusterRecordRepo, RecordClusteringService recordClusteringService, ReactorFederatedLockService lockService) {
		this.clusterRecordRepo = clusterRecordRepo;
		this.recordClusteringService = recordClusteringService;
		this.lockService = lockService;
	}
	
	public void prioritiseReprocessing(String idStr) {
		synchronized (priorityReprocessingQueue) {
			priorityReprocessingQueue.add(idStr);
		}
	}
	
	private List<UUID> getDedupedPriorityBatch() {
		log.info( "Next batch of up to [{}] from priority queue", BATCH_SIZE );
		final List<UUID> toProcess = new ArrayList<>( BATCH_SIZE );
		synchronized (priorityReprocessingQueue) {
			
			Iterator<String> queue = priorityReprocessingQueue.iterator();
			while (toProcess.size() < BATCH_SIZE && queue.hasNext()) {
				try {
					UUID id = UUID.fromString(queue.next());
					
					toProcess.add(id);
				} catch (IllegalArgumentException ex) {
					// Invalid UUID somehow. Ignore.
				}
			}
		}
		return toProcess.size() > 0 ? toProcess : null;
	}
	
	/**
	 * This stream will process all in the priority queue until complete.
	 * We don't track or dedupe additions here that happens later in the flow when
	 * we sanity check that the cluster is still outdated, and hasn't been brought
	 * up to date by any other processing.
	 * 
	 * @return List of UUIDs representing a stream of "batches" to process
	 */

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	protected Flux<Collection<UUID>> prioritySubscription() {
		return Mono.fromSupplier( this::getDedupedPriorityBatch )
			.expand( batch -> batch == null ? Mono.empty() : Mono.fromSupplier(this::getDedupedPriorityBatch))
			.flatMap( list -> Flux.fromIterable(list)
				.flatMap( this::checkHousekeepingStillRequired )
				.collectList());
	}
	
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	protected Mono<Set<UUID>> databaseEntitySubscription() {
		
		log.info( "Next batch of up to [{}] from database", BATCH_SIZE );
		return Flux.from( clusterRecordRepo.getClusterIdsWithOutdatedUnprocessedBibs(IngestService.getProcessVersion(), BATCH_SIZE) )
			.collect(Collectors.toUnmodifiableSet())
			.filter( Predicate.not( Set::isEmpty ));
	}
	
	@Transactional(propagation = Propagation.MANDATORY)
	protected Mono<UUID> checkHousekeepingStillRequired( UUID id ) {
		return Mono.from( clusterRecordRepo.getClusterIdIfOutdated(IngestService.getProcessVersion(), id) );
	}
	
	// Intentionally not transactional as the disperse op uses a new transaction.
	private Mono<UUID> performClusterHousekeeping(UUID id) {
		log.info("Disperse and recluster [{}]", id);
		return recordClusteringService.disperseAndRecluster(id);
	}
	

	private static final String TASK_LOCK = "cluster-housekeeping";

	
	private void jobSubscriber(long time, long count) {

		if (count < 1) {
			return;
		}

		float rate = (float) count / ((float) time / 1000f);

		Duration elapsed = Duration.ofMillis(time);

		log.info("Processed {} clusters with oudated bibs. Total time {} hours, {} minute and {} seconds (rate of {} per second)",
				count, elapsed.toHoursPart(), elapsed.toMinutesPart(), elapsed.toSecondsPart(), "%,.2f".formatted(rate));
	}
	
	// Used in `condition` expression of the reprocess Schedule. 
	boolean completed = false;
	
	private void errorSubscriber(Throwable t) {
		log.error("Error during import job", t);
	}

	@AppTask
	@Scheduled(initialDelay = "10s", fixedDelay = "30s")
	protected void reprocess() {
		
		if (completed) {
			// NOOP
			return;
		}
		
		log.info("Running cluster housekeeping task");
		
		// Deduped up to 100 candidates.
		prioritySubscription()
			.publishOn( Schedulers.boundedElastic() )
			.subscribeOn( Schedulers.boundedElastic() )
			.switchIfEmpty( databaseEntitySubscription() )
			.concatMap( toCheck -> {
				return Flux.fromIterable( toCheck )
					.concatMap( clusterId -> performClusterHousekeeping(clusterId)
						.onErrorResume(e -> {
							log.error("Failed to flag [{}] for recluster, even after retries.");
							return Mono.empty();
						}));
			})
			.count()
			.map( count -> {
				
				if (count > 0) return count;
				
				// Log that we are disabling the job
				log.info("No more clusters requiring housekeeping. Task terminating");
				
				// No more records to process. Stop the job by setting the completed flag to true.
				// This should stop repeat jobs from firing.
				completed = true;
				
				return count;
			})
			.elapsed()
			.transformDeferred( lockService.withLockOrEmpty(TASK_LOCK) )
			.doOnSuccess( res -> {
				if (res == null) {
					log.info("Cluster Housekeeping allready running (NOOP)");
				}
			})
			.subscribe(
				TupleUtils.consumer(this::jobSubscriber),
				this::errorSubscriber);
			
	}
}
