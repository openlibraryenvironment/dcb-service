package org.olf.dcb.availability.job;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Function;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.olf.dcb.availability.job.AvailabilityCheckChunk.AvailabilityCheckChunkBuilder;
import org.olf.dcb.availability.job.BibAvailabilityCount.BibAvailabilityCountBuilder;
import org.olf.dcb.availability.job.BibAvailabilityCount.Status;
import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.clustering.RecordClusteringService.MissingAvailabilityInfo;
import org.olf.dcb.core.clustering.model.ClusterRecord;
import org.olf.dcb.core.model.BibRecord;
import org.olf.dcb.core.model.Item;
import org.olf.dcb.core.model.ReferenceValueMapping;
import org.olf.dcb.core.clustering.model.ClusterRecord;
import org.olf.dcb.core.svc.BibRecordService;
import org.olf.dcb.core.svc.LocationToAgencyMappingService;
import org.olf.dcb.indexing.SharedIndexService;
import org.olf.dcb.item.availability.AvailabilityReport;
import org.olf.dcb.item.availability.AvailabilityReport.Error;
import org.olf.dcb.item.availability.LiveAvailabilityService;
import org.olf.dcb.operations.OperationsService;
import org.olf.dcb.storage.BibAvailabilityCountRepository;
import org.reactivestreams.Publisher;
import org.slf4j.event.Level;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.Environment;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.json.tree.JsonNode;
import io.micronaut.retry.annotation.Retryable;
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
import reactor.util.function.Tuples;
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

/**
 *
 * the run method here is an @AppTask. AppTasks can be skipped by adding the class name to the DCB_SCHEDULED_TASKS_SKIPPED
 * environment variable. So DCB_SCHEDULED_TASKS_SKIPPED="....,AvailabilityCheckJob,..." should skip this task in a given
 * installation
 * @See AppTaskAwareScheduledMethodProcessor
 */

@Slf4j
@Singleton
@Requires(notEnv = Environment.TEST)
@ApplicableChunkTypes( AvailabilityCheckChunk.class )
public class AvailabilityCheckJob implements Job<MissingAvailabilityInfo>, JobChunkProcessor {
	
	// II Adding CLUSTER_CHECK_CONCURRENCY Because I'm concerned that we are creating a large queue of
	// clusters to check and then limiting the concurrency of the lookups for bibs in that cluster
	// So although we never check more than 100 bibs at a time, we can have many more than 100 of those flows in play
	
	// SO: I have removed that particular throttle as it's a bug if we are generating the next chunk before we have finished processing this one.
	// I have fixed that in the runner. 
		// The problem I think may be that inside a chunk (checkClusterAvailability) the number of bibs attached to a cluster 
    // can be large and this can generate an unbounded (in theory, in practice tho, large enough to exhaust the DB pool) set 
    // of events that causes the exception that CLUSTER_CHECK_CONCURRENCY avoided. This is obviously a worse problem on old style
    // bad clusters - but we're definitely still seeing it on GIL - see DCB-1976 for details tho.

	// We're now back to seeing this error
  // ^[[36m11:20:14.275^[[0;39m ^[[37m[reactor-tcp-epoll-2]^[[0;39m ^[[39mDEBUG^[[0;39m ^[[35mo.o.d.i.a.LiveAvailabilityService^[[0;39m - getAvailableItems got items, progress to availability check
	// ^[[36m11:20:14.278^[[0;39m ^[[37m[reactor-tcp-epoll-2]^[[0;39m ^[[1;31mERROR^[[0;39m ^[[35mo.o.d.a.job.AvailabilityCheckJob^[[0;39m - Error saving/updating bibcount
	// io.r2dbc.postgresql.client.ReactorNettyClient$RequestQueueException: Cannot exchange messages because the request queue limit is exceeded
	//   at io.r2dbc.postgresql.client.ReactorNettyClient$BackendMessageSubscriber.lambda$addConversation$1(ReactorNettyClient.java:752)
	//   Suppressed: reactor.core.publisher.FluxOnAssembly$OnAssemblyException:
	// Assembly trace from producer [reactor.core.publisher.FluxCreate] :
	//   reactor.core.publisher.Flux.create(Flux.java:650)
	//   io.r2dbc.postgresql.client.ReactorNettyClient$BackendMessageSubscriber.addConversation(ReactorNettyClient.java:735)
  //
  // It looks to me like throttleFetchBySourceSystem is still capable of generating more pending updates than we are able to process
	// 

	
	
	private static final Duration TIMEOUT = Duration.of(30, ChronoUnit.SECONDS);
	private static final String FILTERS = "none";
	
	final static UUID NS = UUIDUtils.nameUUIDFromNamespaceAndString(UUIDUtils.NAMESPACE_DNS, "org.olf.dcb.availability");
	
	private final AvailabilityCheckJobConfig jobConfig;
	
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
			var locationCode = item.getLocationCode();
			if ( locationCode == null ) {
				// Null location. Log and skip.
				if ( log.isWarnEnabled() ) {
					log.warn("No location code returned for item local ID [{}] when checking availability for bib [{}]", item.getLocalId(), bib.getId());
				}
				// Skip.
				continue;
			}
			
			// Add the data.
			// Ensure the location code is not more than 128 characters
			if (locationCode.length() > 128) {
				locationCode = locationCode.substring(0, 127);
			}
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
  private final OperationsService operations;

	public AvailabilityCheckJob(LiveAvailabilityService liveAvailabilityService, SharedIndexService sharedIndexService,
			BibRecordService bibRecordService, LocationToAgencyMappingService locationToAgencyMappingService,
			HostLmsService hostLmsService, BibAvailabilityCountRepository bibCounts,
			ReactiveJobRunnerService jobRunnerService, ReactorFederatedLockService lockService, OperationsService operations,
			AvailabilityCheckJobConfig jobConfig) {
		
		this.liveAvailabilityService = liveAvailabilityService;
		this.sharedIndexService = sharedIndexService;
		this.locationToAgencyMappingService = locationToAgencyMappingService;
		this.hostLmsService = hostLmsService;
		this.bibCounts = bibCounts;
		this.bibRecordService = bibRecordService;
		this.jobRunnerService = jobRunnerService;
		this.lockService = lockService;
		this.operations = operations;
		this.jobConfig = jobConfig;
	}

	private static BibAvailabilityCountBuilder getAvailabilityCountDefaults( BibRecord bib ) {
		
		return BibAvailabilityCount.builder()
			.hostLms( bib.getSourceSystemId() )
			.bibId( bib.getId() )
			.status( Status.UNMAPPED );
	}
	
	private Mono<BibAvailabilityCount> doSave( BibAvailabilityCount bac ) {
		
		final BibAvailabilityCount theCount = withId(bac);
		
		return Mono.from(bibCounts.saveOrUpdate(theCount))
			.thenReturn(theCount)
			.onErrorComplete(t -> {
				log.error("Error saving/updating bibcount", t);
				
				// Return true always to ensure we complete and suppress the error signal.
				return true;
			});
	}
	
	private Mono<BibAvailabilityCount> updateMappingIfRequired( BibAvailabilityCount count ) {
		final Instant now = Instant.now();
		
		if (count.getStatus() == Status.MAPPED) {
			// Nothing to do, just tag the last updated.
			return Mono.just(
					count.toBuilder()
						.lastUpdated(now)
						.gracePeriodEnd(now.plus( jobConfig.getMappedRecheckGracePeriod() ))
						.build())
				.flatMap(this::doSave);
		}
		
		if (count.getRemoteLocationCode() == null) {
			// No point looking up a "null" code
			return Mono.just(
					count.toBuilder()
						.status(Status.UNMAPPED)
						.internalLocationCode(null)
						.mappingResult( "No remote location code" )
						.lastUpdated(now)
						.gracePeriodEnd(now.plus( jobConfig.getRecheckGracePeriod() ))
						.build())
				.flatMap(this::doSave);
		}
		
		// Attempt to map and save the results
		return hostLmsService.idToCode(count.getHostLms())
			.flatMap(code -> locationToAgencyMappingService.findLocationToAgencyMapping(code, count.getRemoteLocationCode()))
			.mapNotNull( ReferenceValueMapping::getToValue )
			
			// Mapping found
			.map( mappedCode -> count.toBuilder()
				.status(Status.MAPPED)
				.internalLocationCode(mappedCode)
				.mappingResult( "Mapped [%s -> %s]".formatted(count.getRemoteLocationCode(), mappedCode) )
				.lastUpdated(now)
				.gracePeriodEnd(now.plus( jobConfig.getMappedRecheckGracePeriod() ))
				.build())
			
			// No mapping...
			.switchIfEmpty(Mono.just(count)
				.map( BibAvailabilityCount::toBuilder )
				.map( builder -> builder
					.status(Status.UNMAPPED)
					.internalLocationCode(null)
					.mappingResult( "No mapping was found" )
					.lastUpdated(now)
					.gracePeriodEnd(now.plus( jobConfig.getRecheckGracePeriod() ))
					.build()))

			.flatMap(this::doSave);
		
		
//		locationToAgencyMappingService.findLocationToAgencyMapping(filters, filters);
	}
	
	public Flux<BibAvailabilityCount> throttleFetchBySourceSystem( Collection<UUID> ids ) {
		
		final int totalConcurrency = jobConfig.getConcurrency().getInstanceWide()
				.orElseGet(() -> Math.max( Runtime.getRuntime().availableProcessors() / 4, 5));

		
		final int externalPerSystem = jobConfig.getConcurrency().getPerSource();

		log.info("Setting totalConcurrency={}, externalPerSystem={}",totalConcurrency,externalPerSystem);
		
		return bibRecordService.findAllByIdIn( ids )
			.collectMultimap( bib -> bib.getSourceSystemId().toString() )
			.flatMapIterable(Map::values)
			.concatMap( sameSourceBibs -> {
				return Flux.fromIterable(sameSourceBibs)
					.window(externalPerSystem)
					.delayElements(Duration.of(1, ChronoUnit.SECONDS))
					.concatMap( items -> items
						.flatMap( this::checkSingleBib ), 0); // No prefetching
			}, totalConcurrency);
	}
	
	@Transactional
	public Mono<Map<String, Collection<BibAvailabilityCount>>> checkClusterAvailability( Collection<UUID> bibs ) {
		// Manifest the bibs that need updating.
		return throttleFetchBySourceSystem( bibs )
			// Trying to control the rate at which DB updates are generated - in response to the exception
			// described in comments at the top of the class
			.buffer(15) // process n at a time
			.concatMap(batch -> Flux.fromIterable(batch)
				.flatMap(this::updateMappingIfRequired, 3) // 3 concurrent per batch
			)
			.collectMultimap(count -> count.getBibId().toString());
	}
	

	@Retryable(attempts = "3", delay = "2.5", multiplier = "2")
	protected Mono<AvailabilityReport> remoteBibFetch( BibRecord bib ) {
		return liveAvailabilityService.checkBibAvailability(bib, TIMEOUT, FILTERS);
	}
	
	private Flux<BibAvailabilityCount> checkSingleBib ( BibRecord bib ) {
		
		return Mono.just( bib )
			.flatMap(this::remoteBibFetch)
			.onErrorResume(e -> Mono.just(AvailabilityReport.ofErrors(AvailabilityReport.Error.builder()
					.message("Error when fetching bib availability for [%s] %s".formatted(bib.getId().toString(), e))
					.build())))
			.flatMapMany( rep -> updateCountsFromAvailabilityReport(bib, rep) );
	}
	
	/**
	 * Ideally this would be moved and centralized. For Brevity we are adding here to try and benefit from the
	 * live lookups that happen as part of user interaction.
	 */
	@Transactional
	public Mono<UUID> updateCountsForSingleBibAvailability( BibRecord bib, AvailabilityReport rep ) {
		final var updateIndex = Mono.fromSupplier( bib::getContributesTo )
			.map( ClusterRecord::getId )
			.map( crId -> {
				sharedIndexService.add(crId);
				return crId;
			});
		
		return availabilityReportToCountEntries(bib, rep)
			.onErrorResume(e -> {
				log.error("Error building map from availability reports", e);
				return Flux.empty();
			})
			.flatMap( this::updateMappingIfRequired )
			.then( updateIndex );
	}
	
	private Flux<BibAvailabilityCount> updateCountsFromAvailabilityReport( BibRecord bib, AvailabilityReport rep ) {
		return availabilityReportToCountEntries(bib, rep)
			.onErrorResume(e -> {
				log.error("Error building map from availability reports", e);
				return Flux.empty();
			});
	}
	
	private Map<String, Map<String, Collection<BibAvailabilityCount>>> reindexAffectedClusters (Map<String, Map<String, Collection<BibAvailabilityCount>>> vals) {
		for (var clusterCount : vals.entrySet()) {
			var clusterId = clusterCount.getKey();
			
			if (log.isDebugEnabled()) {
				var availabilityMap = clusterCount.getValue();
				final String deets = availabilityMap.entrySet().stream()
					.map(e -> "%s=%s".formatted(e.getKey(), e.getValue()))
					.collect(Collectors.joining(","));
				log.debug("Avaiability for [{}]: {}", clusterId, deets);
			}
			
			sharedIndexService.update( UUID.fromString( clusterId ) );
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
		
		final boolean transitionedIntoOfficeHours = operations.getOfficeHours().isInsideHours();
		if (transitionedIntoOfficeHours)
			log.debug("Making this the last chunk as we've entered office hours");
		
		return AvailabilityCheckChunk.builder()
			.jobId(getId())
			.lastChunk( transitionedIntoOfficeHours ) // Default this chunk to last if we have gone into outside hours.
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
	
	private long getGraceNanos() {
		final Duration grace = jobConfig.getRecheckGracePeriod();
		return grace.isNegative() ? grace.toNanos() * -1 : grace.toNanos();
	}
	
	private Mono<JobChunk<MissingAvailabilityInfo>> getChunk( Optional<JsonNode> resumption ) {

		log.info("Creating next chunk subscription");

		final int pageSize = jobConfig.getPageSize();
		final Instant updatedBefore = Instant.now()
			.minusNanos( getGraceNanos() );
		
		log.info("Using configured values totalConcurrency:[{}], updatedBefore[{}]", pageSize, updatedBefore);
		
		return Mono.just( Tuples.of(pageSize, updatedBefore) )
			.doOnNext( _v -> log.debug ("Producing next set of availability lookup") )
			
			.flatMapMany( TupleUtils.function( bibRecordService::findMissingAvailability ))
			.collectList()
			.flatMapIterable( Function.identity() )
			.reduceWith( this::defaultChunkBuilder, (builder, item) -> builder.dataEntry(item) )
			.map( AvailabilityCheckChunkBuilder::build )
			
			// Temporarily adding a 50ms delay - steve to review
			.delayElement(Duration.ofMillis(50))
			
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
			.transformDeferred( lockService.withLockOrEmpty(JOB_LOCK) )
			.doOnSuccess( res -> {
				if (res == null) {
					log.info(JOB_NAME + " allready running (NOOP)");
				}
			})
			.transformDeferred( operations::subscribeOnlyOutsideOfficeHours )
			.doOnSuccess( res -> {
				if (res == null) {
					log.info(JOB_NAME + " skipping as set to run ouside office hours");
				}
			})
			.subscribe(
					TupleUtils.consumer(this::jobSubscriber),
					this::errorSubscriber);
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
					f.doOnNext(Level.INFO, item -> log.debug("Check availability for {}", item.toString()))) )
				
				.collectMultimap( info -> info.clusterId().toString(), MissingAvailabilityInfo::bibId )
				.flatMapIterable( Map::entrySet )
				
				.flatMap(entry -> checkClusterAvailability(entry.getValue())
					.map(locationMap -> Map.entry( entry.getKey(), locationMap )))
				.collectMap(Entry::getKey, Entry::getValue)	
				.map( this::reindexAffectedClusters )
			.then( Mono.just(chunk) );
	}
}
