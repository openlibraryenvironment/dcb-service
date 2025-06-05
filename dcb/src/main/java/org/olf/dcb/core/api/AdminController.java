package org.olf.dcb.core.api;

import static io.micronaut.http.MediaType.APPLICATION_JSON;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.Optional;
import java.util.UUID;
import java.util.Map;
import java.util.HashMap;

import org.olf.dcb.core.api.serde.ImportCommand;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.ProcessState;
import org.olf.dcb.core.model.RecordCountSummary;
import org.olf.dcb.core.svc.HouseKeepingService;
import org.olf.dcb.indexing.SharedIndexLiveUpdater;
import org.olf.dcb.indexing.SharedIndexLiveUpdater.ReindexOp;
import org.olf.dcb.request.fulfilment.PatronRequestService;
import org.olf.dcb.request.resolution.SupplierRequestService;
import org.olf.dcb.security.RoleNames;
import org.olf.dcb.storage.BibRepository;
//import org.olf.dcb.stats.StatsService;
import org.olf.dcb.storage.PatronRequestRepository;
import org.olf.dcb.storage.ProcessStateRepository;
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
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.security.annotation.Secured;
import io.micronaut.validation.Validated;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
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
	private final Environment env;

	public AdminController(PatronRequestService patronRequestService, SupplierRequestService supplierRequestService,
//			StatsService statsService,
			PatronRequestRepository patronRequestRepository,
			ProcessStateRepository processStateRepository,
			DCBConfigurationService configurationService, 
			BibRepository bibRepository,
			Optional<SharedIndexLiveUpdater> sharedIndexUpdater, HouseKeepingService housekeeping,
      Environment env) {

		this.patronRequestService = patronRequestService;
		this.supplierRequestService = supplierRequestService;
//		this.statsService = statsService;
		this.patronRequestRepository = patronRequestRepository;
		this.processStateRepository = processStateRepository;
		this.configurationService = configurationService;
		this.bibRepository = bibRepository;
		this.sharedIndexUpdater = sharedIndexUpdater;
		this.housekeeping = housekeeping;
		this.env = env;
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
	public Mono<Map> getConfig() {
		log.info("Get configuration");
    Map<String,Object> result = new HashMap();
    Map<String,Object> env_report = new HashMap();

    for (PropertySource source : env.getPropertySources()) {
      for (String key : source) {
        env_report.put(key, source.get(key));
      }
    }

    result.put("env_report",env_report);

    return Mono.just(result);
	}

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
	public Mono<MutableHttpResponse<String>> reprocess() {
		return housekeeping
			.reprocessAll()
			.map(HttpResponse.accepted()::<String>body);
	}
	
	@Post(uri = "/validateClusters", produces = APPLICATION_JSON)
	public Mono<MutableHttpResponse<String>> validateClusters() {
		return housekeeping
			.validateClusters()
			.map(HttpResponse.accepted()::<String>body);
	}
	
	@Get(uri = "/threads", produces = MediaType.TEXT_PLAIN)
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
}
