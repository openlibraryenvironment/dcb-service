package org.olf.dcb.availability.job;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.olf.dcb.availability.job.AvailabilityCheckChunk.AvailabilityCheckChunkBuilder;
import org.olf.dcb.availability.job.BibAvailabilityCount.BibAvailabilityCountBuilder;
import org.olf.dcb.availability.job.BibAvailabilityCount.Status;
import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.model.BibRecord;
import org.olf.dcb.core.model.Item;
import org.olf.dcb.core.model.ReferenceValueMapping;
import org.olf.dcb.core.svc.BibRecordService;
import org.olf.dcb.core.svc.LocationToAgencyMappingService;
import org.olf.dcb.core.svc.RecordClusteringService.MissingAvailabilityInfo;
import org.olf.dcb.indexing.SharedIndexService;
import org.olf.dcb.item.availability.AvailabilityReport;
import org.olf.dcb.item.availability.AvailabilityReport.Error;
import org.olf.dcb.item.availability.LiveAvailabilityService;
import org.olf.dcb.storage.BibAvailabilityCountRepository;
import org.reactivestreams.Publisher;
import org.slf4j.event.Level;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.json.tree.JsonNode;
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
import services.k_int.federation.reactor.ReactorFederatedLockService;
import services.k_int.jobs.Job;
import services.k_int.jobs.JobChunk;
import services.k_int.jobs.JobChunkProcessor;
import services.k_int.jobs.JobChunkProcessor.ApplicableChunkTypes;
import services.k_int.jobs.ReactiveJobRunnerService;
import services.k_int.micronaut.scheduling.processor.AppTask;
import services.k_int.serde.JsonBuilder;
import services.k_int.utils.ReactorUtils;
import services.k_int.utils.UUIDUtils;

@Slf4j
@Singleton
@ApplicableChunkTypes( AvailabilityCheckChunk.class )
public class AvailabilityCheckJob implements Job<MissingAvailabilityInfo>, JobChunkProcessor {
	
	private static final int CLUSTER_MAX_DEFAULT = 200;
	private static final int EXTERNAL_FETCH_CONCURRENCY = 100;
	private static final Duration TIMEOUT = Duration.of(30, ChronoUnit.SECONDS);
	private static final String filters = "none";
	
	final static UUID NS = UUIDUtils.nameUUIDFromNamespaceAndString(UUIDUtils.NAMESPACE_DNS, "org.olf.dcb.availability");
	
	private static BibAvailabilityCount withId( BibAvailabilityCount count ) {
		
		var location = count.getRemoteLocationCode();
		
		final var uuidSource = Stream.of(count.getHostLms(), count.getBibId(), location != null ? location : "")
			.map(Objects::requireNonNull)
			.map(Object::toString)
			.collect(Collectors.joining(":"));
		
		return count.toBuilder()
			.id(UUIDUtils.nameUUIDFromNamespaceAndString(NS, uuidSource))
			.build();
	}
		
	private Flux<BibAvailabilityCount> availabilityReportToCountEntries( BibRecord bib, AvailabilityReport availabilityReport ) {
		
		final var errors = availabilityReport.getErrors();
		final var data = availabilityReport.getItems();
		
		if ( data.isEmpty() ) {
			if ( errors.isEmpty() ) {
				// No errors and no items... 0 availability.
				return Flux.just(getAvailabilityCountDefaults( bib )
					.count( 0 )
					.mappingResult("LMS adapter gave empty response for [%s], please consult the logs.".formatted(bib.getId()))
					.build());
			}
			
			String combinedErrors =  errors.stream()
				.map(Error::getMessage)
				.collect(Collectors.joining("\n"));
			
			// Truncate for the DB field...
			if (combinedErrors.length() > 255) {
				combinedErrors = combinedErrors.substring(0, 252) + "...";
			}
			
			// Errors but no data.
			return Flux.just(getAvailabilityCountDefaults( bib )
				.count( 0 )
				.mappingResult(combinedErrors)
				.build());
		}
		
		// Collate location codes from the items as counts
		final Map<String, Integer> locationCounts = new HashMap<>();
		for ( Item item : data ) {
			final var locationCode = item.getLocationCode();
			if ( locationCode == null ) {
				// Null location. Log and skip.
				if ( log.isWarnEnabled() ) {
					log.warn("No location code returned for item local ID [{}] when checking availability for bib [{}]", item.getLocalId(), bib.getId());
				}
				// Skip.
				continue;
			}
			
			// Add the data.
			int count = locationCounts.getOrDefault(locationCode, 0);
			locationCounts.put(locationCode, (count+1));
		}
		
		// Convert the map into a flux of items.
		return Flux.fromIterable( locationCounts.entrySet() )
			.map( entry -> getAvailabilityCountDefaults( bib )
				.remoteLocationCode( entry.getKey() )
				.count( entry.getValue() )
				.build());
	}
	
	private final LiveAvailabilityService liveAvailabilityService;
	private final SharedIndexService sharedIndexService; 
	private final LocationToAgencyMappingService locationToAgencyMappingService;
	private final HostLmsService hostLmsService;
	private final BibAvailabilityCountRepository bibCounts;
	
	private final BibRecordService bibRecordService;
	private final ReactiveJobRunnerService jobRunnerService;
  private final ReactorFederatedLockService lockService;

	public AvailabilityCheckJob( LiveAvailabilityService liveAvailabilityService, SharedIndexService sharedIndexService, BibRecordService bibRecordService, LocationToAgencyMappingService locationToAgencyMappingService, HostLmsService hostLmsService, BibAvailabilityCountRepository bibCounts, ReactiveJobRunnerService jobRunnerService, ReactorFederatedLockService lockService ) {
		this.liveAvailabilityService = liveAvailabilityService;
		this.sharedIndexService = sharedIndexService;
		this.locationToAgencyMappingService = locationToAgencyMappingService;
		this.hostLmsService = hostLmsService;
		this.bibCounts = bibCounts;
		this.bibRecordService = bibRecordService;
		this.jobRunnerService = jobRunnerService;
		this.lockService = lockService;
	}

	private static BibAvailabilityCountBuilder getAvailabilityCountDefaults( BibRecord bib ) {
		
		return BibAvailabilityCount.builder()
			.hostLms( bib.getSourceSystemId() )
			.bibId( bib.getId() )
			.status( Status.UNMAPPED );
	}
	
	private Mono<BibAvailabilityCount> updateMappingIfRequired( BibAvailabilityCount count ) {
		if (count.getStatus() == Status.MAPPED) {
			// Nothing to do.
			return Mono.just(count);
		}
		
		return hostLmsService.idToCode(count.getHostLms())
			.flatMap(code -> locationToAgencyMappingService.findLocationToAgencyMapping(code, count.getRemoteLocationCode()))
			.mapNotNull( ReferenceValueMapping::getToValue )
			
			// Mapping found
			.map( mappedCode -> count.toBuilder()
				.status(Status.MAPPED)
				.internalLocationCode(mappedCode)
				.mappingResult( "Mapped [%s -> %s]".formatted(count.getRemoteLocationCode(), mappedCode) )
				.lastUpdated(Instant.now())
				.build())
			
			// No mapping...
			.switchIfEmpty(Mono.just(count)
				.map( BibAvailabilityCount::toBuilder )
				.map( builder -> builder
					.status(Status.UNMAPPED)
					.internalLocationCode(null)
					.mappingResult( "No mapping was found" )
					.lastUpdated(Instant.now())
					.build()))

			// Set the ID.
			.map(AvailabilityCheckJob::withId)
			.flatMap(bib -> Mono.from(bibCounts.saveOrUpdate(bib))
				.thenReturn(bib))
			.onErrorComplete(t -> {
				log.error("Error saving/updating bibcount", t);
				
				// Return true always to ensure we complete and suppress the error signal.
				return true;
			});
		
		
//		locationToAgencyMappingService.findLocationToAgencyMapping(filters, filters);
	}
	
	@Transactional
	public Mono<Map<String, Collection<BibAvailabilityCount>>> checkClusterAvailability( Collection<UUID> bibs ) {
		
		// Manifest the bibs that need updating.
		return bibRecordService.getAllByIdIn( bibs )
			.flatMap( this::checkSingleBib, EXTERNAL_FETCH_CONCURRENCY )
			
			// Convert to and store the Location Entry
			.flatMap( this::updateMappingIfRequired )

			.collectMultimap(count -> count.getBibId().toString());
	}
	
	private Flux<BibAvailabilityCount> checkSingleBib ( BibRecord bib ) {
		return liveAvailabilityService.checkBibAvailability(bib, TIMEOUT, filters)
				.onErrorResume(e -> Mono.just(AvailabilityReport.ofErrors(AvailabilityReport.Error.builder()
						.message("Error when fetching bib availability for [%s] %s".formatted(bib.getId().toString(), e))
						.build())))
				.flatMapMany( rep -> availabilityReportToCountEntries(bib, rep) )
				.onErrorResume(e -> {
					log.error("Error building map from availability reports", e);
					return Flux.empty();
				});
		
	}
	
	private Map<String, Map<String, Collection<BibAvailabilityCount>>> reindexAffectedClusters (Map<String, Map<String, Collection<BibAvailabilityCount>>> vals) {
		for (var clusterCount : vals.entrySet()) {
			var availabilityMap = clusterCount.getValue();
			var clusterId = clusterCount.getKey();
			
			final String deets = availabilityMap.entrySet().stream()
				.map(e -> "%s=%s".formatted(e.getKey(), e.getValue()))
				.collect(Collectors.joining(","));
			
			log.info("Avaiability for [{}]: {}", clusterId, deets);
			sharedIndexService.update( UUID.fromString( clusterId ));
		}
		
		return vals;
	}
	
	private void jobSubscriber(long time, long count) {

		if (count < 1) {
			log.info("No records to process");
			return;
		}

		float rate = (float) count / ((float) time / 1000f);

		Duration elapsed = Duration.ofMillis(time);

		log.info("Processed {} bibs with missing availability. Total time {} hours, {} minute and {} seconds (rate of {} per second)",
				count, elapsed.toHoursPart(), elapsed.toMinutesPart(), elapsed.toSecondsPart(), "%,.2f".formatted(rate));
	}

	private void errorSubscriber(Throwable t) {
		log.error("Error during import job", t);
	}
	
	private AvailabilityCheckChunkBuilder defaultChunkBuilder() {
		
		return AvailabilityCheckChunk.builder()
			.jobId(getId())
			.checkpoint(JsonBuilder.obj(o -> o
				.key("runStarted", vals -> vals.str(Instant.now().toString()))
				.key("lastFetched", vals -> vals.str(Instant.now().toString()))));
		
	}
	
	private static AvailabilityCheckChunk chunkPostProcess(AvailabilityCheckChunk chunk) {
		
		if (!chunk.getData().isEmpty()) return chunk;
		
		return chunk.toBuilder()
			.lastChunk(true)
			.build();		
	}
	
	private Mono<JobChunk<MissingAvailabilityInfo>> getChunk( Optional<JsonNode> resumption ) {
		
		return Mono.just( CLUSTER_MAX_DEFAULT )
			.flatMapMany( bibRecordService::findMissingAvailability )
			.reduceWith( this::defaultChunkBuilder, (builder, item) -> builder.dataEntry(item) )
			.map( AvailabilityCheckChunkBuilder::build )
			.map( AvailabilityCheckJob::chunkPostProcess );
	}
	
	// Handle requesting availability chunks and update cluster chunks.
	@AppTask
	@ExecuteOn(TaskExecutors.BLOCKING)
	@Scheduled(initialDelay = "10s", fixedDelay = "2m")
	public void run() {
		
		Mono.just( this )
			.publishOn( Schedulers.boundedElastic() )
			.subscribeOn( Schedulers.boundedElastic() )
			.flatMapMany( jobRunnerService::processJobInstance )
			.map(chunk -> Optional.ofNullable(chunk.getData()) // Extract resource count.
					.map( Collection::size )
					.map( Integer::longValue )
					.orElse(0L))
			.reduce( Long::sum )
			.elapsed()
			.transformDeferred(lockService.withLockOrEmpty(JOB_LOCK))
			.doOnSuccess( res -> {
				if (res == null) {
					log.info(JOB_NAME + "allready running (NOOP)");
				}
			})
			.subscribe(
					TupleUtils.consumer(this::jobSubscriber),
					this::errorSubscriber);
		
		
//		getChunk()
//			.publishOn( Schedulers.boundedElastic() )
//			.subscribeOn( Schedulers.boundedElastic() )
//			.transform( ReactorUtils.withFluxLogging(log, f ->
//					f.doOnNext(Level.INFO, item -> log.info("Check availability for {}", item.toString()))) )
//			.collectMultimap( info -> info.clusterId().toString(), MissingAvailabilityInfo::bibId )
//			.flatMapIterable( Map::entrySet )
//			.flatMap(entry -> checkClusterAvailability(entry.getValue())
//				.map(locationMap -> Map.entry( entry.getKey(), locationMap )))
//			.collectMap(Entry::getKey, Entry::getValue)
//			.subscribe( this::reindexAffectedClusters, this::logError );
	}

	private static final String JOB_NAME = "Availability job";
	private static final String JOB_LOCK = "availability-job";

	@Override
	public @NonNull String getName() {
		return JOB_NAME;
	}

	@Override
	@SingleResult
	public Publisher<JobChunk<MissingAvailabilityInfo>> resume(JsonNode lastCheckpoint) {
		if (log.isDebugEnabled()) {
			log.debug("Resume with [{}]", lastCheckpoint);
		}
		
		return getChunk(Optional.of(lastCheckpoint));
	}

	@Override
	@SingleResult
	public Publisher<JobChunk<MissingAvailabilityInfo>> start() {
		log.info("Initializing availability job");
		return getChunk(Optional.empty());
	}
	
	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	public <T> Publisher<JobChunk<T>> processChunk(JobChunk<T> chunk) {
		AvailabilityCheckChunk acc = (AvailabilityCheckChunk)chunk;
		
		return Flux.fromIterable( acc.getData() )
				// Special logging transformer only adds the operators if the log level is equal or greater.
				.transform( ReactorUtils.withFluxLogging(log, f ->
					f.doOnNext(Level.INFO, item -> log.info("Check availability for {}", item.toString()))) )
				
				.collectMultimap( info -> info.clusterId().toString(), MissingAvailabilityInfo::bibId )
				.flatMapIterable( Map::entrySet )
				
				.flatMap(entry -> checkClusterAvailability(entry.getValue())
					.map(locationMap -> Map.entry( entry.getKey(), locationMap )))
				.collectMap(Entry::getKey, Entry::getValue)	
				.map( this::reindexAffectedClusters )
			.then( Mono.just(chunk) );
	}
}
