package org.olf.reshare.dcb.core.api;

import static io.micronaut.http.MediaType.APPLICATION_JSON;

import javax.validation.Valid;

import org.olf.reshare.dcb.processing.PlacePatronRequestCommand;
import org.olf.reshare.dcb.request.fulfilment.PatronRequestService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
import io.micronaut.validation.Validated;
import io.swagger.v3.oas.annotations.tags.Tag;
import reactor.core.publisher.Mono;

@Validated
@Secured(SecurityRule.IS_ANONYMOUS)
@Controller
@Tag(name = "Patron Request API")
public class PatronRequestController {
	public static final Logger log = LoggerFactory.getLogger(PatronRequestController.class);

	private final PatronRequestService patronRequestService;

	public PatronRequestController(PatronRequestService patronRequestService) {
		this.patronRequestService = patronRequestService;
	}

	@SingleResult
	@Post(value = "/patrons/requests/place", consumes = APPLICATION_JSON)
	public Mono<HttpResponse<PlacePatronRequestCommand>> placePatronRequest(
		@Body @Valid Mono<PlacePatronRequestCommand> command) {

		log.debug("REST, place patron request: {}", command);

		return patronRequestService.placePatronRequest(command)
			.map(HttpResponse::ok);
	}
}
