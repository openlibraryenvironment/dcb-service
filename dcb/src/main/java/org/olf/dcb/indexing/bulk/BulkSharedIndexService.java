package org.olf.dcb.indexing.bulk;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.olf.dcb.core.clustering.RecordClusteringService;
import org.olf.dcb.core.clustering.model.ClusterRecord;
import org.olf.dcb.indexing.SharedIndexConfiguration;
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
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import services.k_int.micronaut.PublisherTransformationService;

@Transactional(propagation = Propagation.NOT_SUPPORTED)
public abstract class BulkSharedIndexService implements SharedIndexService {
	private static final Logger log = LoggerFactory.getLogger(BulkSharedIndexService.class);

	private final AtomicBoolean circuitOpen = new AtomicBoolean(false);
	
	private final AtomicBoolean rateThresholdOpen = new AtomicBoolean(false);

	private FluxSink<String> theSink = null;
	
	private final RecordClusteringService clusters;
	
	// Maximum size of each chunk to send to the shared index.
	final int maxSize;
	
	final Duration throttleTimeout;

	private final SharedIndexQueueRepository sharedIndexQueueRepository;
	private final PublisherTransformationService publisherTransformer;
	
	public PublisherTransformationService getPublisherTransformer() {
		return publisherTransformer;
	}

	protected BulkSharedIndexService( RecordClusteringService clusters, SharedIndexQueueRepository sharedIndexQueueRepository, PublisherTransformationService publisherTransformationService, SharedIndexConfiguration conf ) {
		
		this.clusters = clusters;
		this.maxSize = conf.maxResourceListSize().orElse(2000); // Default to 1500
		this.throttleTimeout = conf.minUpdateFrequency().orElse(Duration.ofSeconds(3)); // Default 5 seconds.
		this.sharedIndexQueueRepository = sharedIndexQueueRepository;
		this.publisherTransformer = publisherTransformationService;
		initializeQueue();
	}

	protected void overflow( UUID id ) {
		
		log.debug("Backpressure overflow handler");
		saveSingleEntry(id, Optional.empty()).block();
	}
	
	protected Flux<List<UUID>> collectAndDedupe(Flux<String> in) {
		// Collect at double output rate
		return in
			.windowTimeout( maxSize * 2, throttleTimeout.dividedBy(2) )
			.windowTimeout( 2, throttleTimeout )
			.flatMap( sources -> Flux.concat( sources ).distinct().buffer(maxSize))
//			.delayElements( Duration.ofSeconds(2) )
			.map( bulk -> {
				
				var deduped = bulk.stream().distinct().map(UUID::fromString).toList();
				if (log.isDebugEnabled()) log.debug("Got list of {} index items ({} when deduped)", bulk.size(), deduped.size());
				
				return deduped;
			});
	}
	
	protected void initializeQueue() {
		Flux.create( this::setSink )
			.subscribeOn(Schedulers.boundedElastic())
			.publishOn(Schedulers.boundedElastic())
			.transform( this::addHooks )
//			.transform( publisherTransformer::executeOnBlockingThreadPool )
			.transform( this::collectAndDedupe )
			.transform( this::expandAndProcess )
			.doOnComplete(() -> log.info("Subscription finalised"))
			.retry(10)
			.subscribe( null, t -> {
				log.atError().log("Index service cannot be initialized", t);
			});
	}

	private Flux<String> addHooks(Flux<String> source) {

		// Using shared allows for multiple receivers of the emissions without multiple subscriptions.
		Flux<String> common = source.share();
		
		// Calculate our trigger values.
		final Duration trigger = throttleTimeout.dividedBy(3);
		final long triggerMillis = trigger.toMillis();
		
		common
			.buffer(trigger)
			.map(List::size)
			.filter(size -> {
				log.trace("Collected {} in {}", size, trigger);
				long ratePerItem = size > 0 ? (triggerMillis / size) : 0;
				log.trace("Rate per item: {}", ratePerItem);
				return ratePerItem <= 1000; // At least 1 second or less per item
				
			}) // First part emits the rate if it's sustained
			
			.doOnNext( rate -> {
				if (rateThresholdOpen.get()) {
					log.trace("Rate threshold already opened: NOOP");
					return;
				}
				
				// Open it and run hooks.
				rateThresholdOpen.set(true);
				
				log.debug("Running rate threshold open hooks");
				rateThresholdOpenHook();
				
			})
			.sampleTimeout( rate -> Mono.delay(throttleTimeout) )
			.subscribe(rate -> {
				if (!rateThresholdOpen.get()) {
					log.trace("Rate threshold already closed: NOOP");
					return;
				}
				
				// close it and run hooks
				rateThresholdOpen.set(false);
				
				log.debug("Running rate threshold closed hooks");
				rateThresholdClosedHook(); 
			});
		
		return common;
	}

	@Override
	public Publisher<List<IndexOperation<UUID, ClusterRecord>>> expandAndProcess( Flux<List<UUID>> idFlux ) {
		return idFlux
			.concatMap( this::manifestCluster )
			.filter( Predicates.not( List::isEmpty ) )
			.flatMap(ops -> this.offloadToImplementation(ops)
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
				}), 3);
	}

	@NonNull
	@CircuitBreaker(reset = "2m", attempts = "3", maxDelay = "5s", throwWrappedException = true )
	protected Flux<List<IndexOperation<UUID, ClusterRecord>>> offloadToImplementation( final List<IndexOperation<UUID, ClusterRecord>> ops ) {
		return Flux.just(ops)
			.subscribeOn(Schedulers.boundedElastic())
			.publishOn(Schedulers.boundedElastic())
//			.transform( publisherTransformer::executeOnBlockingThreadPool )
			.flatMap( data -> Flux.from( doOnNext(data) ))
//				.transform( publisherTransformer::executeOnBlockingThreadPool ) )
			.concatMap( data -> afterIndex(data))
//				 .transform( publisherTransformer::executeOnBlockingThreadPool ) )
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
		
		return Mono.just(itemIds)
			.publishOn(Schedulers.boundedElastic())
			.flatMapMany( clusters::findAllByIdInListWithBibs )
			.map( this::clusterRecordToIndexOperation )
			.collectMultimap( IndexOperation::type )
			.map( typeMap -> {
				
				final List<IndexOperation<UUID, ClusterRecord>> records = new ArrayList<>(); 
				
				final StringBuffer msg = new StringBuffer();
				
				typeMap.forEach((type, recs) -> {
					msg.append(", " + type.name() + ": " + recs.size());
					records.addAll(recs);
				});
				
				if (msg.length() > 0) {
					log.info("Bulking: {}", msg.substring(2) );
				}
				
				return records;
			})
			.subscribeOn(Schedulers.boundedElastic());
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
	
	private void setSink(FluxSink<String> sink) {
		this.theSink = sink;
	}

	private void checkSink() {
		if (theSink == null) throw new IllegalStateException("Event cannot be handled as the data sink has not been initialized."); 
	}
	
	private void queueId(UUID clusterId) {		
		checkSink();
		if (clusterId != null) this.theSink.next(clusterId.toString());
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
	
	protected void rateThresholdOpenHook( ) {
		// NOOP
	}
	
	protected void rateThresholdClosedHook( ) {
		// NOOP
	}

	@PreDestroy
	protected void cleanup() {
		if (theSink != null) {
			theSink.complete();
			theSink = null;
		}
	}
}
