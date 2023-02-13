package org.olf.reshare.dcb.core.api;

import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.*;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
import io.micronaut.validation.Validated;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.olf.reshare.dcb.core.api.datavalidation.PatronRequestCommand;
import org.olf.reshare.dcb.request.fulfilment.PatronRequestService;
import org.olf.reshare.dcb.storage.PatronRequestRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import javax.validation.Valid;

@Validated
@Secured(SecurityRule.IS_ANONYMOUS)
@Controller
@Tag(name = "Patron Request API")
public class PatronRequestController  {

	public static final Logger log = LoggerFactory.getLogger(PatronRequestController.class);

	private final PatronRequestService patronRequestService;
	private final PatronRequestRepository patronRequestRepository;

	public PatronRequestController(PatronRequestService patronRequestService, PatronRequestRepository patronRequestRepository) {
		this.patronRequestService = patronRequestService;
		this.patronRequestRepository = patronRequestRepository;
	}

	@SingleResult
	@Post("/patrons/requests/place")
	public Mono<HttpResponse<PatronRequestCommand>> placePatronRequest(@Body @Valid Mono<PatronRequestCommand> patronRequestCommand) {
		log.debug("REST, place patron request: {}", patronRequestCommand);

		patronRequestCommand = patronRequestService.savePatronRequest(patronRequestCommand);
		return patronRequestCommand.map(HttpResponse::ok);
	}
}
