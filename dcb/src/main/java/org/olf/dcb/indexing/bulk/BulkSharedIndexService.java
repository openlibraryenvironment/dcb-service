package org.olf.dcb.indexing.bulk;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.olf.dcb.core.model.clustering.ClusterRecord;
import org.olf.dcb.core.svc.RecordClusteringService;
import org.olf.dcb.indexing.SharedIndexService;
import org.olf.dcb.indexing.model.SharedIndexQueueEntry;
import org.olf.dcb.indexing.storage.SharedIndexQueueRepository;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.javaparser.quality.NotNull;

import graphql.com.google.common.base.Predicates;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.data.model.Pageable;
import io.micronaut.retry.annotation.CircuitBreaker;
import io.micronaut.retry.exception.CircuitOpenException;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.scheduling.annotation.Scheduled;
import io.micronaut.transaction.TransactionDefinition.Propagation;
import io.micronaut.transaction.annotation.Transactional;
import jakarta.annotation.PreDestroy;
import reactor.core.publisher.BufferOverflowStrategy;
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
	final int maxSize = 1000;
	
	final Duration throttleTimeout = Duration.ofSeconds(3);

	private final SharedIndexQueueRepository sharedIndexQueueRepository;
	
	protected BulkSharedIndexService( RecordClusteringService clusters, SharedIndexQueueRepository sharedIndexQueueRepository ) {
		
		this.clusters = clusters;
		this.sharedIndexQueueRepository = sharedIndexQueueRepository;
		initializeQueue();
	}

	protected void overflow( UUID id ) {
		
		log.debug("Backpressure overflow handler");
		saveSingleEntry(id, Optional.empty()).block();
	}
	
	protected void initializeQueue() {
		Flux.create(this::setSink)
			.onBackpressureBuffer( maxSize * 2, this::overflow, BufferOverflowStrategy.DROP_LATEST)
			.windowTimeout(maxSize, throttleTimeout)
			.concatMap( source -> source.buffer(), 0)
			.doOnNext( bulk -> {
				if (log.isDebugEnabled()) log.debug("Got list of {} index items", bulk.size());
			})
			.delayElements( Duration.ofSeconds(2) )
			.transform(this::expandAndProcess)
			.doOnComplete(() -> log.info("Subscription finalised"))
			.retry(10)
			.subscribe( null, t -> {
				log.atError().log("Index service cannot be initialized", t);
			});
	}
	
	@Override
	public Publisher<List<IndexOperation<UUID, ClusterRecord>>> expandAndProcess( Flux<List<UUID>> idFlux ) {
		return idFlux
			.concatMap( this::manifestCluster )
			.filter( Predicates.not( List::isEmpty ) )
			.concatMap(ops -> this.offloadToImplementation(ops)
				.onErrorResume(e -> {
					
					if (CircuitOpenException.class.isAssignableFrom(e.getClass())) {
						// Ensure the global flag to prevent scheduled backup job is set.
						flagCircuitOpen();
	
						// Log in trace to prevent noise.
						log.atInfo().log("Index service circuit broken temporarily to prevent IO flooding.");
						log.atTrace().log("Circuit open cause: ", e.getCause() );
					}
					
	//				return Mono.empty();
					return queueInBackupJob( ops );
				}));
	}

	@NonNull
	@CircuitBreaker(reset = "2m", attempts = "3", maxDelay = "5s", throwWrappedException = true )
	protected Flux<List<IndexOperation<UUID, ClusterRecord>>> offloadToImplementation( final List<IndexOperation<UUID, ClusterRecord>> ops ) {
		return Flux.just(ops)
			.flatMap( this::doOnNext )
			.concatMap( this::afterIndex )
			.doOnNext(_item -> this.flagCircuitClosed());
	}
	
	@NonNull
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	protected Mono<List<IndexOperation<UUID, ClusterRecord>>> afterIndex( final List<IndexOperation<UUID, ClusterRecord>> ops ) {
		List<UUID> queueEntryIds = ops.stream()
			.map( IndexOperation::id )
			.toList();
		
		return Mono.from(sharedIndexQueueRepository.deleteAllByClusterIdIn(queueEntryIds))
			.doOnNext(count -> {
				if (count > 0) {
					log.debug("{} items were removed from backup queue", count);
				}})
			.thenReturn(ops);
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

	@NonNull
	@SingleResult
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	protected Mono<List<IndexOperation<UUID, ClusterRecord>>> queueInBackupJob( final List<IndexOperation<UUID, ClusterRecord>> ops ) {
		return Flux.fromIterable(ops)
			.flatMap( op -> {
				
				UUID id = op.id();
				Optional<Instant> upd = Optional.ofNullable(op.doc())
					.flatMap( cr ->
						Optional.ofNullable(cr.getDateUpdated())
							.or(() -> Optional.ofNullable(cr.getDateCreated())));
				
				return this.saveSingleEntry(id, upd);
			})
			.doOnNext( entry -> log.atTrace().log("Added backup entry for cluster [{}]", entry.getClusterId()))
			.then( Mono.just(ops) );
	}

	@NonNull
	@SingleResult
	@Transactional
	protected Mono<? extends SharedIndexQueueEntry> saveSingleEntry( @NotNull UUID crId, @NotNull Optional<Instant> timestamp ) {
		return Mono.from(sharedIndexQueueRepository.save(
			SharedIndexQueueEntry.builder()
			 	.clusterId(crId)
			 	.clusterDateUpdated(timestamp.orElse(Instant.now()))
			 	.build()))
		.doOnError( ex -> {
		  // Log the error but move on.
			log.atError().log("Error adding Cluster to index queue in the database", ex);
			// Recover.
//			return Mono.empty();
		});
	}

	@NonNull
	@SingleResult
	@Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
	protected Mono<List<IndexOperation<UUID, ClusterRecord>>> manifestCluster (final List<UUID> itemIds) {
		
		return Flux.just(itemIds)
			.flatMap(clusters::findAllByIdInListWithBibs)
			.map( this::clusterRecordToIndexOperation )
			.collectList();
	}
	
	private IndexOperation<UUID, ClusterRecord> clusterRecordToIndexOperation( @NotNull final ClusterRecord cr ) {
		if (Boolean.TRUE.equals(cr.getIsDeleted())) {
			return IndexOperation.delete( cr.getId() );
		}
		
		return IndexOperation.update(cr.getId(), cr);
	}

	@Transactional(readOnly = true)
	protected Flux<UUID> getBackupQueue() {
		
		final int pageSize = maxSize * 2; // Double page size, but stagger the elements.
		final int staggerByMillis = 5;

		// We always fetch the first page
		final Pageable pageable = Pageable.from(0, pageSize);
		
		return Mono.from( sharedIndexQueueRepository.findAllOrderByClusterDateUpdatedAsc(pageable) )
			.flatMapIterable( page -> page.map(SharedIndexQueueEntry::getClusterId) )
			.takeUntil( _i -> this.circuitOpen.get())
			.delayElements(Duration.ofMillis(staggerByMillis));
	}
	
	@ExecuteOn(value = TaskExecutors.BLOCKING)
	@Scheduled(fixedDelay = "2s", initialDelay = "30s")
	public void indexBackupQueue() {
		if (log.isTraceEnabled()) {
			log.trace("Running backup job.");
		}
		
		getBackupQueue()
			.doOnError(this::databaseJobError)
			.doOnNext( this::queueId )
			.blockLast();
		// Sadly we need this method to block, so that the "fixedDelay" works above.
		
	}
	
	private void databaseJobError ( Throwable error ) {
		log.error("Error running scheduled job", error);
	}

	@NonNull
	protected abstract Publisher<List<IndexOperation<UUID, ClusterRecord>>> doOnNext ( final List<IndexOperation<UUID, ClusterRecord>> item);
	
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
