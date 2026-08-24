package org.olf.dcb.core.api;

import static io.micronaut.http.MediaType.APPLICATION_JSON;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;

import io.micronaut.context.BeanProvider;
import io.micronaut.context.annotation.Value;
import org.olf.dcb.core.api.serde.ImportCommand;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.ProcessState;
import org.olf.dcb.core.model.RecordCountSummary;
import org.olf.dcb.core.svc.HouseKeepingService;
import org.olf.dcb.dataimport.job.SourceRecordService;
import org.olf.dcb.indexing.SharedIndexLiveUpdater;
import org.olf.dcb.indexing.SharedIndexLiveUpdater.ReindexOp;
import org.olf.dcb.request.fulfilment.PatronRequestService;
import org.olf.dcb.request.resolution.SupplierRequestService;
import org.olf.dcb.security.RoleNames;
import org.olf.dcb.storage.BibRepository;
//import org.olf.dcb.stats.StatsService;
import org.olf.dcb.storage.PatronRequestRepository;
import org.olf.dcb.storage.ProcessStateRepository;
import org.olf.dcb.tracking.TrackingHelpers;
import org.olf.dcb.utils.DCBConfigurationService;
import org.olf.dcb.utils.DCBConfigurationService.ConfigImportResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.*;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.authentication.Authentication;
import io.micronaut.security.rules.SecurityRule;
import io.micronaut.validation.Validated;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Nullable;
import jakarta.validation.Valid;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.function.TupleUtils;

import io.micronaut.context.env.Environment;
import io.micronaut.context.env.PropertySource;

@Controller("/admin")
@Validated
@Secured(RoleNames.ADMINISTRATOR)
@Tag(name = "Admin API")
public class AdminController {
	private static final Logger log = LoggerFactory.getLogger(AdminController.class);

	private final PatronRequestService patronRequestService;
	private final SupplierRequestService supplierRequestService;
	private final PatronRequestRepository patronRequestRepository;
	private final ProcessStateRepository processStateRepository;
	private final BibRepository bibRepository;
//	private final StatsService statsService;
	private final DCBConfigurationService configurationService;
	private final Optional<SharedIndexLiveUpdater> sharedIndexUpdater;
	private final HouseKeepingService housekeeping;
	// BeanProvider, not a direct injection. SourceRecordService registers entity event listeners and
	// carries the scheduled import tasks, and a controller is instantiated when the embedded server
	// starts - injecting it directly pulls all of that into every context that has an HTTP server,
	// including API tests, which then fail on data the ingest job created underneath them.
	// HostLmsService already holds this same bean this way, for the same reason.
	private final BeanProvider<SourceRecordService> sourceRecordServiceProvider;
	private final Environment env;
	private final TrackingHelpers trackingHelpers;
	private final Long globalActiveRequestLimit;
	private final String globalTrackingInterval;



	public AdminController(PatronRequestService patronRequestService, SupplierRequestService supplierRequestService,
//			StatsService statsService,
			PatronRequestRepository patronRequestRepository,
			ProcessStateRepository processStateRepository,
			DCBConfigurationService configurationService, 
			BibRepository bibRepository,
			Optional<SharedIndexLiveUpdater> sharedIndexUpdater, HouseKeepingService housekeeping,
			BeanProvider<SourceRecordService> sourceRecordServiceProvider,
      Environment env, TrackingHelpers trackingHelpers, @Value("${dcb.globals.active-request-limit:25}") Long globalActiveRequestLimit, @Value("${dcb.tracking.interval:5m}") String globalTrackingInterval) {

		this.patronRequestService = patronRequestService;
		this.supplierRequestService = supplierRequestService;
//		this.statsService = statsService;
		this.patronRequestRepository = patronRequestRepository;
		this.processStateRepository = processStateRepository;
		this.configurationService = configurationService;
		this.bibRepository = bibRepository;
		this.sharedIndexUpdater = sharedIndexUpdater;
		this.housekeeping = housekeeping;
		this.sourceRecordServiceProvider = sourceRecordServiceProvider;
		this.env = env;
		this.trackingHelpers = trackingHelpers;
		this.globalActiveRequestLimit = globalActiveRequestLimit;
		this.globalTrackingInterval = globalTrackingInterval;
	}

	// ToDo: The tests seem to want to be able to call this without any auth - that
	// needs fixing
	@SingleResult
	@Get(uri = "/patrons/requests/{id}", produces = APPLICATION_JSON)
	public Mono<PatronRequestAdminView> getPatronRequest(@PathVariable("id") final UUID id) {

		log.debug("REST, get patron request by id: {}", id);

		return patronRequestService.findById(id)
				.flatMap(this::assembleAdminView);
	}

	@Operation(summary = "Browse Requests", description = "Paginate through the list of Patron Requests", parameters = {
			@Parameter(in = ParameterIn.QUERY, name = "number", description = "The page number", schema = @Schema(type = "integer", format = "int32"), example = "1"),
			@Parameter(in = ParameterIn.QUERY, name = "size", description = "The page size", schema = @Schema(type = "integer", format = "int32"), example = "100") })
	@Get("/patrons/requests{?pageable*}")
	public Mono<Page<PatronRequest>> list( @Parameter(hidden = true) @Valid Pageable pageable ) {

		if (pageable == null) {
			pageable = Pageable.from(0, 100);
		}

		return Mono.from(patronRequestRepository.queryAll(pageable));
	}

	private Mono<PatronRequestAdminView> assembleAdminView( PatronRequest patronRequest ) {
		return Mono.zip(
			Mono.just(patronRequest),
			supplierRequestService.findAllActiveSupplierRequestsFor(patronRequest),
			patronRequestService.findAllAuditsFor(patronRequest))
		
		.map(TupleUtils.function(PatronRequestAdminView::from));
	}

//	@SingleResult
//	@Get(uri = "/statistics", produces = APPLICATION_JSON)
//	public Mono<StatsService.Report> getStatsReport() {
//		StatsService.Report report = statsService.getReport();
//		log.debug("report: {}", report);
//		return Mono.just(report);
//	}

  @Get(uri = "/recordCounts", produces = APPLICATION_JSON)
  public Flux<RecordCountSummary> getRecordCounts() {
    return Flux.from(bibRepository.getIngestReport());
  }

  @Get(uri = "/processStates", produces = APPLICATION_JSON)
  public Flux<ProcessState> getProcessState() {
    return Flux.from(processStateRepository.findAll());
  }


	// public Mono<ConfigImportResult> importCfg(@Nullable @Body ImportCommand
	// importCommand) {

	@Post(uri = "/cfg", produces = APPLICATION_JSON)
	public Mono<ConfigImportResult> importCfg(@Body @Valid ImportCommand ic) {
		log.info("Import configuration request {}", ic);
		return configurationService.importConfiguration(ic.getProfile(), ic.getUrl());
	}

	@Get(uri = "/cfg", produces = APPLICATION_JSON)
	public Mono<Map<String, Object>> getConfig() {
		log.info("Get configuration");
		Map<String, Object> result = new HashMap<>();
		Map<String, Object> env_report = new HashMap<>();

		for (PropertySource source : env.getPropertySources()) {
			for (String key : source) {
				Object value = source.get(key);
				// Converting all values to string to make sure they are serializable
				env_report.put(key, value != null ? value.toString() : null);
			}
		}

		result.put("env_report", env_report);
		return Mono.just(result);
	}

//	@Secured(SecurityRule.IS_ANONYMOUS)
	@Post(uri = "/reindex{/operation}", produces = APPLICATION_JSON)
	public Mono<MutableHttpResponse<Object>> reindex(Optional<ReindexOp> operation) {
		
		ReindexOp op = operation.orElse(ReindexOp.START);

		log.info("reindex request... {}", op);
		
		return Mono.justOrEmpty(sharedIndexUpdater)
			.zipWith(Mono.just(op))
			
			.flatMap( TupleUtils.function((indexService, theOp) -> 
				indexService
					.reindexAllClusters(op)
					.thenReturn(HttpResponse.accepted())))
			
			.defaultIfEmpty(HttpResponse.notFound());
	}
	
	@Post(uri = "/dedupe/matchpoints", produces = MediaType.TEXT_PLAIN)
	public Mono<MutableHttpResponse<String>> dedupeMatchPoints() {
		return housekeeping
			.dedupeMatchPoints()
			.map(HttpResponse.accepted()::<String>body);
	}

	@Post(uri = "/reprocess", produces = APPLICATION_JSON)
	public Mono<MutableHttpResponse<String>> reprocess( @QueryValue("criteria") @Nullable String criteria) {
		return housekeeping
			.reprocess(criteria)
			.map(HttpResponse.accepted()::<String>body);
	}
	
	@Post(uri = "/reprocess/{clusterId}", produces = APPLICATION_JSON)
	public Mono<MutableHttpResponse<String>> reprocess(@PathVariable UUID clusterId) {
		return housekeeping
			.reprocessClusterBibs(clusterId)
			.map(HttpResponse.accepted()::<String>body);
	}
	
	/**
	 * Re-fetch the records a Host LMS holds but DCB does not, without a full re-harvest.
	 *
	 * Repairing a stalled harvest does not recover what it previously skipped - delta harvesting only
	 * surfaces records modified after the watermark, and skipped records are older than it.
	 *
	 * 202 rather than a result: the sweep is thousands of sequential requests against the target
	 * system and must not be held open by an HTTP request. Poll /admin/sourceImport/status.
	 */
	@Post(uri = "/sourceImport/{hostLmsCode}/reconcile", produces = APPLICATION_JSON)
	public Mono<MutableHttpResponse<String>> reconcileSourceRecords(@PathVariable String hostLmsCode,
		@QueryValue("reason") @Nullable String reason, @Nullable Authentication authentication) {

		log.warn("Source record reconciliation requested for [{}] by [{}], reason [{}]",
			hostLmsCode, nameOf(authentication), reason);

		return sourceRecordServiceProvider.get()
			.startReconciliation(hostLmsCode)
			.map(HttpResponse.accepted()::<String>body);
	}

	/**
	 * Clear a source import checkpoint so the next scheduled run starts a full harvest.
	 *
	 * The heavy remedy - it re-walks the entire catalogue of the target system - so prefer
	 * reconcile when the damage is partial. Synchronous because it deletes a single row.
	 */
	@Post(uri = "/sourceImport/{hostLmsCode}/resetCheckpoint", produces = APPLICATION_JSON)
	public Mono<MutableHttpResponse<String>> resetSourceImportCheckpoint(@PathVariable String hostLmsCode,
		@QueryValue("reason") @Nullable String reason, @Nullable Authentication authentication) {

		log.warn("Source import checkpoint reset requested for [{}] by [{}], reason [{}]",
			hostLmsCode, nameOf(authentication), reason);

		return sourceRecordServiceProvider.get()
			.resetCheckpointFor(hostLmsCode)
			.map(HttpResponse.ok()::<String>body);
	}

	@Get(uri = "/sourceImport/status", produces = APPLICATION_JSON)
	public Mono<Map<String, Object>> getSourceImportRecoveryStatus() {
		return Mono.just(sourceRecordServiceProvider.get().getReconcileStatus());
	}

	// Both recovery actions are destructive enough to want an audit trail of who asked for them.
	private static String nameOf(Authentication authentication) {
		return authentication != null ? authentication.getName() : "User not detected";
	}

	@Post(uri = "/validateClusters", produces = APPLICATION_JSON)
	public Mono<MutableHttpResponse<String>> validateClusters() {
		return housekeeping
			.validateClusters()
			.map(HttpResponse.accepted()::<String>body);
	}

	@Post(uri = "/validateClusters/{clusterId}", produces = APPLICATION_JSON)
	public Mono<MutableHttpResponse<String>> validateClusters(
      @PathVariable UUID clusterId) {
		return housekeeping
			.validateSingleCluster(clusterId)
			.map(HttpResponse.accepted()::<String>body);
	}


	
	@Get(uri = "/threads", produces = MediaType.TEXT_PLAIN)
	// JVM-wide stack and monitor inspection can pause or contend; keep it off Netty.
	@ExecuteOn(TaskExecutors.BLOCKING)
	public String threads() {
	    StringBuffer threadDump = new StringBuffer(System.lineSeparator());
	    ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();

	    // Loop through all the threads
	    for(ThreadInfo threadInfo : threadMXBean.dumpAllThreads(true, true)) {
	    	// Dump all the info from the thread
	        threadDump.append(threadInfo.toString());
	    }

	    // Return the info about the threads
	    return(threadDump.toString());
	}

	@Get(uri = "/trackingConfiguration", produces = APPLICATION_JSON)
	public Mono<Map<String, Object>> getTrackingConfiguration() {
		Map<PatronRequest.Status, Duration> durations = trackingHelpers.getDurations();

		Map<PatronRequest.Status, String> formattedDurations = durations.entrySet().stream()
			.collect(Collectors.toMap(
				Map.Entry::getKey,
				entry -> {
					Duration duration = entry.getValue();
					if (duration == null) {
						return "Not set";
					}
					// Put the tracking durations into human-readable format (HH:mm:ss)
					long totalSeconds = duration.getSeconds();
					long hours = totalSeconds / 3600;
					long minutes = (totalSeconds % 3600) / 60;
					long seconds = totalSeconds % 60;

					return String.format("%02d:%02d:%02d", hours, minutes, seconds);
				}
			));

		// Add in global active request limit for useful context
		Map<String, Object> apiResponse = new HashMap<>();
		apiResponse.put("trackingIntervals", formattedDurations);
		apiResponse.put("globalTrackingInterval", globalTrackingInterval);
		apiResponse.put("globalActiveRequestLimit", this.globalActiveRequestLimit);

		return Mono.just(apiResponse);
	}
}
