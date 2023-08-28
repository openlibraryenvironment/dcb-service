package org.olf.dcb.core.api;

import static io.micronaut.http.HttpResponse.status;
import static io.micronaut.http.HttpStatus.NOT_FOUND;
import static io.micronaut.http.MediaType.APPLICATION_JSON;
import static reactor.function.TupleUtils.function;

import java.util.List;
import java.util.UUID;

import io.micronaut.http.MutableHttpResponse;
import io.micronaut.security.authentication.Authentication;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;

import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.PatronRequestAudit;
import org.olf.dcb.core.model.SupplierRequest;
import org.olf.dcb.request.fulfilment.PatronRequestService;
import org.olf.dcb.request.resolution.SupplierRequestService;
import org.olf.dcb.stats.StatsService;
import org.olf.dcb.storage.PatronRequestAuditRepository;
import org.olf.dcb.storage.PatronRequestRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.validation.Valid;

import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.annotation.Consumes;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.http.annotation.Body;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuple3;
import reactor.util.function.Tuples;

import org.olf.dcb.utils.DCBConfigurationService;
import org.olf.dcb.utils.DCBConfigurationService.ConfigImportResult;

import java.security.Principal;
import io.micronaut.core.annotation.Introspected;


import io.micronaut.serde.annotation.Serdeable;
import lombok.Builder;
import lombok.Data;




@Produces(APPLICATION_JSON)
@Controller("/admin")
@Secured(SecurityRule.IS_ANONYMOUS)
// @Secured({ "ADMIN" })
@Tag(name = "Admin API")
public class AdminController {
	private static final Logger log = LoggerFactory.getLogger(AdminController.class);

	private final PatronRequestService patronRequestService;
	private final SupplierRequestService supplierRequestService;
	private final PatronRequestRepository patronRequestRepository;
	private final PatronRequestAuditRepository patronRequestAuditRepository;
	private final StatsService statsService;
	private final DCBConfigurationService configurationService;

	public AdminController(PatronRequestService patronRequestService,
		SupplierRequestService supplierRequestService,
		StatsService statsService,
		PatronRequestRepository patronRequestRepository,
		PatronRequestAuditRepository patronRequestAuditRepository,
		DCBConfigurationService configurationService) {

		this.patronRequestService = patronRequestService;
		this.supplierRequestService = supplierRequestService;
		this.statsService = statsService;
		this.patronRequestRepository = patronRequestRepository;
		this.patronRequestAuditRepository = patronRequestAuditRepository;
		this.configurationService = configurationService;
	}

	// ToDo: The tests seem to want to be able to call this without any auth - that needs fixing
	@SingleResult
	@Get(uri="/patrons/requests/{id}", produces = APPLICATION_JSON)
	public Mono<HttpResponse<PatronRequestAdminView>> getPatronRequest(@PathVariable("id") final UUID id) {

		log.debug("REST, get patron request by id: {}", id);

		return patronRequestService.findById(id)
			.flatMap(this::findSupplierRequests)
			.flatMap(function(this::findAudits))
			.map(function(this::mapToView))
			.map(HttpResponse::ok);
	}

	@Secured({ "ADMIN" })
        @Operation(
                summary = "Browse Requests",
                description = "Paginate through the list of Patron Requests",
                parameters = {
                        @Parameter(in = ParameterIn.QUERY, name = "number", description = "The page number", schema = @Schema(type = "integer", format = "int32"), example = "1"),
                        @Parameter(in = ParameterIn.QUERY, name = "size", description = "The page size", schema = @Schema(type = "integer", format = "int32"), example = "100")}
        )
        @Get("/patrons/requests{?pageable*}")
        public Mono<Page<PatronRequest>> list(@Parameter(hidden = true) @Valid Pageable pageable,
                                              Authentication authentication) {

                if (pageable == null) {
                        pageable = Pageable.from(0, 100);
                }

                return Mono.from(patronRequestRepository.queryAll(pageable));
        }


	private Mono<Tuple2<PatronRequest, List<SupplierRequest>>> findSupplierRequests(PatronRequest patronRequest) {
		return supplierRequestService.findAllSupplierRequestsFor(patronRequest)
				.map(supplierRequests -> Tuples.of(patronRequest, supplierRequests));
	}

	private Mono<Tuple3<PatronRequest, List<SupplierRequest>, List<PatronRequestAudit>>> findAudits(
		PatronRequest patronRequest, List<SupplierRequest> supplierRequests) {
		return patronRequestService.findAllAuditsFor(patronRequest)
			.map(audits -> Tuples.of(patronRequest, supplierRequests, audits));
	}

	private PatronRequestAdminView mapToView(PatronRequest patronRequest,
		List<SupplierRequest> supplierRequests, List<PatronRequestAudit> audits) {

		return PatronRequestAdminView.from(patronRequest, supplierRequests, audits);
	}

	@Secured({ "ADMIN" })
	@SingleResult
	@Get(uri="/statistics", produces = APPLICATION_JSON)
	public Mono<StatsService.Report> getStatsReport() {
		StatsService.Report report =statsService.getReport();
		log.debug("report: {}",report);
		return Mono.just(report);
	}


        // public Mono<ConfigImportResult> importCfg(@Nullable @Body ImportCommand importCommand) {

	@Secured({ "ADMIN" })
        @Post(uri="/cfg", produces = APPLICATION_JSON)
        public Mono<ConfigImportResult> importCfg(@Body @Valid ImportCommand ic) {
		log.debug("Import configuration {}",ic);
		return configurationService.importConfiguration(ic.profile, ic.url);
        }

        @Data
        @Builder
        @Serdeable
	public static class ImportCommand {
		String profile;
		String url;
	}

}
