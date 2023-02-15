package org.olf.reshare.dcb.core.api;

import static io.micronaut.http.MediaType.APPLICATION_JSON;

import java.util.UUID;

import org.olf.reshare.dcb.request.fulfilment.PlacePatronRequestCommand;
import org.olf.reshare.dcb.request.fulfilment.PatronRequestService;
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
@Tag(name = "Audit API")
public class AuditController {

	public static final Logger log = LoggerFactory.getLogger(AuditController.class);
	private final PatronRequestService patronRequestService;

	public AuditController(PatronRequestService patronRequestService) {
		this.patronRequestService = patronRequestService;
	}

	@SingleResult
	@Get(value = "/admin/patrons/requests/{id}", produces = APPLICATION_JSON)
	public Mono<HttpResponse<PlacePatronRequestCommand>> getPatronRequest(
		@PathVariable("id") final UUID id) {

		log.debug("REST, get patron request with id: {}", id);

		return patronRequestService.getPatronRequestWithId(id)
			.map(HttpResponse::ok);
	}

}
