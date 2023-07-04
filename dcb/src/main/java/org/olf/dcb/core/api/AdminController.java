package org.olf.dcb.core.api;

import static io.micronaut.http.MediaType.APPLICATION_JSON;

import java.util.List;
import java.util.UUID;

import io.micronaut.security.authentication.Authentication;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;

import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.SupplierRequest;
import org.olf.dcb.request.fulfilment.PatronRequestService;
import org.olf.dcb.request.resolution.SupplierRequestService;
import org.olf.dcb.stats.StatsService;
import org.olf.dcb.storage.PatronRequestRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.validation.Valid;

import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Produces;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
import io.swagger.v3.oas.annotations.tags.Tag;
import reactor.core.publisher.Mono;

@Secured(SecurityRule.IS_ANONYMOUS)
@Produces(APPLICATION_JSON)
@Controller
@Tag(name = "Admin API")
public class AdminController {
	private static final Logger log = LoggerFactory.getLogger(AdminController.class);

	private final PatronRequestService patronRequestService;
	private final SupplierRequestService supplierRequestService;
        private final PatronRequestRepository patronRequestRepository;
	private final StatsService statsService;

	public AdminController(PatronRequestService patronRequestService,
				SupplierRequestService supplierRequestService,
				StatsService statsService,
                                PatronRequestRepository patronRequestRepository) {

		this.patronRequestService = patronRequestService;
		this.supplierRequestService = supplierRequestService;
		this.statsService = statsService;
		this.patronRequestRepository = patronRequestRepository;
	}

	@SingleResult
	@Get(value = "/admin/patrons/requests/{id}", produces = APPLICATION_JSON)
	public Mono<HttpResponse<PatronRequestAdminView>> getPatronRequest(@PathVariable("id") final UUID id) {

		log.debug("REST, get patron request by id: {}", id);

		return patronRequestService.findById(id)
			.zipWhen(this::findSupplierRequests, this::mapToView)
			.map(HttpResponse::ok);
	}

        @Secured(SecurityRule.IS_AUTHENTICATED)
        @Operation(
                summary = "Browse Requests",
                description = "Paginate through the list of Patron Requests",
                parameters = {
                        @Parameter(in = ParameterIn.QUERY, name = "number", description = "The page number", schema = @Schema(type = "integer", format = "int32"), example = "1"),
                        @Parameter(in = ParameterIn.QUERY, name = "size", description = "The page size", schema = @Schema(type = "integer", format = "int32"), example = "100")}
        )
        @Get("/admin/patrons/requests{?pageable*}")
        public Mono<Page<PatronRequest>> list(@Parameter(hidden = true) @Valid Pageable pageable,
                                              Authentication authentication) {

                if (pageable == null) {
                        pageable = Pageable.from(0, 100);
                }

                return Mono.from(patronRequestRepository.findAll(pageable));
        }


	private Mono<List<SupplierRequest>> findSupplierRequests(PatronRequest patronRequest) {
		return supplierRequestService.findAllSupplierRequestsFor(patronRequest);
	}

	private PatronRequestAdminView mapToView(
		PatronRequest patronRequest, List<SupplierRequest> supplierRequests) {

		return PatronRequestAdminView.from(patronRequest, supplierRequests);
	}

	@SingleResult
	@Get(value="/statistics", produces = APPLICATION_JSON)
	public Mono<StatsService.Report> getStatsReport() {
		StatsService.Report report =statsService.getReport();
		log.debug("report: {}",report);
		return Mono.just(report);
	}
}
