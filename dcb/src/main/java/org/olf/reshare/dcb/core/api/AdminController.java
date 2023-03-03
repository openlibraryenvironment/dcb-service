package org.olf.reshare.dcb.core.api;

import static io.micronaut.http.MediaType.APPLICATION_JSON;

import java.util.List;
import java.util.UUID;

import org.olf.reshare.dcb.core.model.PatronRequest;
import org.olf.reshare.dcb.core.model.SupplierRequest;
import org.olf.reshare.dcb.request.fulfilment.PatronRequestService;
import org.olf.reshare.dcb.request.resolution.SupplierRequestService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Produces;
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

	public AdminController(PatronRequestService patronRequestService,
		SupplierRequestService supplierRequestService) {

		this.patronRequestService = patronRequestService;
		this.supplierRequestService = supplierRequestService;
	}

	@SingleResult
	@Get(value = "/admin/patrons/requests/{id}", produces = APPLICATION_JSON)
	public Mono<HttpResponse<PatronRequestAdminView>> getPatronRequest(
		@PathVariable("id") final UUID id) {

		log.debug("REST, get patron request by id: {}", id);

		return patronRequestService.findById(id)
			.flatMap(this::findSupplierRequests)
			.map(AdminController::mapToView)
			.map(HttpResponse::ok);
	}

	private Mono<PatronRequestAndSupplierRequests> findSupplierRequests(
		PatronRequest patronRequest) {

		return supplierRequestService.findAllSupplierRequestsFor(patronRequest)
			.map(supplierRequests -> new PatronRequestAndSupplierRequests(
				patronRequest, supplierRequests));
	}

	private static PatronRequestAdminView mapToView(
		PatronRequestAndSupplierRequests pair) {

		return PatronRequestAdminView.from(pair.patronRequest, pair.supplierRequests);
	}

	private record PatronRequestAndSupplierRequests(PatronRequest patronRequest,
		List<SupplierRequest> supplierRequests) { }
}
