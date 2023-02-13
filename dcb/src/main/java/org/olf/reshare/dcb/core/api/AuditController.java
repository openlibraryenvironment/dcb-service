package org.olf.reshare.dcb.core.api;

import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.*;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.olf.reshare.dcb.core.api.datavalidation.*;
import org.olf.reshare.dcb.request.fulfilment.PatronRequestService;
import org.olf.reshare.dcb.storage.PatronRequestRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.UUID;
import static io.micronaut.http.MediaType.APPLICATION_JSON;

@Secured(SecurityRule.IS_ANONYMOUS)
@Produces(APPLICATION_JSON)
@Controller
@Tag(name = "Audit API")
public class AuditController {

	public static final Logger log = LoggerFactory.getLogger(AuditController.class);
	private final PatronRequestService patronRequestService;
	private final PatronRequestRepository patronRequestRepository;

	public AuditController(PatronRequestService patronRequestService, PatronRequestRepository patronRequestRepository) {
		this.patronRequestService = patronRequestService;
		this.patronRequestRepository = patronRequestRepository;
	}

	@SingleResult
	@Get(value = "/admin/patrons/requests/{id}", produces = APPLICATION_JSON)
	public Mono<HttpResponse<PatronRequestCommand>> getPatronRequest(@PathVariable("id") final UUID id) {
		log.debug("REST, get patron request with id: {}", id);

		Mono<PatronRequestCommand> patronRequestCommand = patronRequestService.getPatronRequestWithId(id);
		return patronRequestCommand.map(HttpResponse::ok);
	}

}
