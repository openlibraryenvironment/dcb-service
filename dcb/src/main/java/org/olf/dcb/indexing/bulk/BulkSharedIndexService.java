package org.olf.dcb.indexing.bulk;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.olf.dcb.core.model.clustering.ClusterRecord;
import org.olf.dcb.core.svc.RecordClusteringService;
import org.olf.dcb.indexing.SharedIndexService;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.javaparser.quality.NotNull;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.retry.annotation.CircuitBreaker;
import io.micronaut.retry.exception.CircuitOpenException;
import io.micronaut.transaction.TransactionDefinition.Propagation;
import io.micronaut.transaction.annotation.Transactional;
import jakarta.annotation.PreDestroy;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Mono;

@Transactional(propagation = Propagation.NOT_SUPPORTED)
public abstract class BulkSharedIndexService implements SharedIndexService {
	private static final Logger log = LoggerFactory.getLogger(BulkSharedIndexService.class);
	
	private final AtomicBoolean circuitOpen = new AtomicBoolean(false);

	private FluxSink<UUID> theSink = null;
	
	private final RecordClusteringService clusters;
	
	// Maximum size of each chunk to send to the shared index.
	final int maxSize = 200;
	
	final Duration throttleTimeout = Duration.ofSeconds(3);
	
	protected BulkSharedIndexService( RecordClusteringService clusters ) {
		
		this.clusters = clusters;
		initializeQueue();
	}
		
	protected void initializeQueue() {
		Flux.create(this::setSink)
			.windowTimeout(maxSize, throttleTimeout)
			.concatMap( source -> source.buffer() )
			
			.doOnNext( bulk -> {
				if (log.isDebugEnabled()) log.debug("Got list of {} index items", bulk.size());
			})
			.concatMap( this::manifestCluster )
			.concatMap(ops -> this.offloadToImplementation(ops)
				.onErrorResume(e -> {
					
					if (CircuitOpenException.class.isAssignableFrom(e.getClass())) {
						// Ensure the global flag to prevent scheduled backup job is set.
						flagCircuitOpen();
	
						// Log in trace to prevent noise.
						log.atInfo().log("Index service circuit broken temporarily to prevent network flooding.");
						log.atTrace().log("Circuit open cause: ", e.getCause() );
					}
					
					return Mono.empty();
//					return queueInBackupJob( ops );
				}))

			.doOnComplete(() -> log.info("Subscription finalised"))
			.repeat()
			.subscribe( null, t -> {
				log.atError().log("Index service unavailable", t);
			});
	}

	@NonNull
	@CircuitBreaker(reset = "2m", attempts = "3", maxDelay = "5s", throwWrappedException = true )
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	protected Flux<List<IndexOperation<UUID, ClusterRecord>>> offloadToImplementation( final List<IndexOperation<UUID, ClusterRecord>> ops ) {
		return Flux.just(ops)
			.flatMap( this::doOnNext )
			.doOnNext(_item -> this.flagCircuitClosed());
	}
	
	private void flagCircuitOpen() {
		if (circuitOpen.compareAndSet(false, true)) {
			// Value was changed to open. Lets log an info level message.
			log.info("Indexing service unavailable. Opening circuit to prevent IO flooding");
		}
	}
	
	private void flagCircuitClosed() {
		if (circuitOpen.compareAndSet(true, false)) {
			// Value was changed to open. Lets log an info level message.
			log.info("Indexing service available. Closing circuit");
		}
	}

//	@NonNull
//	@SingleResult
//	@Transactional(propagation = Propagation.REQUIRES_NEW)
//	protected Mono<List<IndexOperation<UUID, ClusterRecord>>> queueInBackupJob( final List<IndexOperation<UUID, ClusterRecord>> ops ) {
//		return Flux.fromIterable(ops)
//			.map( IndexOperation::doc )
//			.flatMap( this::saveSingleEntry )
//			.doOnNext( entry -> log.atDebug().log("Added backup entry for cluster [{}]", entry.getClusterId()))
//			.then( Mono.just(ops) );
//	}

//	@NonNull
//	@SingleResult
//	@Transactional(propagation = Propagation.MANDATORY)
//	protected Publisher<SharedIndexQueueEntry> saveSingleEntry( ClusterRecord cr ) {
//		return Mono.from(jobQueueRepository.save(
//			SharedIndexQueueEntry.builder()
//			 	.clusterId(cr.getId())
//			 	.clusterDateUpdated(cr.getDateUpdated())
//			 	.build()))
//		.onErrorResume( ex -> {
//		  // Log the error but move on.
//			log.atError().log("Error adding Cluster to index queue in the database", ex);
//			// Recover.
//			return Mono.empty();
//		});
//	}

	@NonNull
	@SingleResult
	@Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
	protected Mono<List<IndexOperation<UUID, ClusterRecord>>> manifestCluster (final List<UUID> itemIds) {
		
		return Flux.just(itemIds)
			.flatMap(clusters::findAllByIdInListWithBibs)
			.map( this::clusterRecordToIndexOperation )
			.collectList();
	}
	
	private IndexOperation<UUID, ClusterRecord> clusterRecordToIndexOperation( @NotNull ClusterRecord cr ) {
		if (Boolean.TRUE.equals(cr.getIsDeleted())) {
			return IndexOperation.delete( cr.getId() );
		}
		
		return IndexOperation.update(cr.getId(), cr);
	}

//	@Transactional(propagation = Propagation.REQUIRES_NEW)
//	protected Flux<UUID> getBackupQueue() {
//
//		// We always fetch the first page
//		final Pageable pageable = Pageable.from(0, maxSize);
//		
//		return Mono.from( jobQueueRepository.findAllOrderByClusterDateUpdatedAsc(pageable) )
//			.expand( actioned -> {
//				if (actioned.getNumberOfElements() < 1) {
//					return Mono.empty();
//				}
//				
//				return Mono.defer(() -> this.deleteActionedAndGetNextChunk(actioned, pageable));
//			})
//			.concatMapIterable( page -> page.getContent().stream().map(SharedIndexQueueEntry::getClusterId).toList() )
//			.takeUntil(_rc -> this.circuitOpen.get())
//			;
//	}
	
//	@Scheduled(fixedDelay = "30s", initialDelay = "30s")
//	public void indexBackupQueue() {
//		getBackupQueue()
//			.subscribe(this::queueId, this::databaseJobError);
//	}
	
//	private void databaseJobError ( Throwable error ) {
//		log.error("Error running scheduled job {}", error);
//	}

//	@Transactional(propagation = Propagation.REQUIRES_NEW)
//	protected Mono<Page<SharedIndexQueueEntry>> deleteActionedAndGetNextChunk( final Page<SharedIndexQueueEntry> actioned, final Pageable chunkParams) {
//		
//		List<UUID> queueEntryIds = actioned
//			.map( SharedIndexQueueEntry::getId )
//			.getContent();
//		
//		return Mono.from(jobQueueRepository.deleteAllByIdIn(queueEntryIds))
//			.doOnNext( count -> log.atDebug().log("Removed {} entries for actioned IDs", count) )
//			.then( Mono.defer( () -> Mono.from( jobQueueRepository.findAllOrderByClusterDateUpdatedAsc(chunkParams))));
//	}

	@NonNull
	protected abstract Publisher<List<IndexOperation<UUID, ClusterRecord>>> doOnNext (List<IndexOperation<UUID, ClusterRecord>> item);
	
	private void setSink(FluxSink<UUID> sink) {
		this.theSink = sink;
	}

	private void checkSink() {
		if (theSink == null) throw new IllegalStateException("Event cannot be handled as the data sink has not been initialized."); 
	}
	
	private void queueId(UUID clusterId) {		
		checkSink();
		this.theSink.next(clusterId);
	}
	
	@Override
	public void add(UUID clusterID) {
		queueId(clusterID);
	}
	
	@Override
	public void delete(UUID clusterID) {
		queueId(clusterID);
	}
	
	@Override
	public void update(UUID clusterID) {
		queueId(clusterID);
	}

	@PreDestroy
	protected void cleanup() {
		if (theSink != null) {
			theSink.complete();
			theSink = null;
		}
	}
}
