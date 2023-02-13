package org.olf.reshare.dcb.core.api;

import javax.validation.Valid;

import org.olf.reshare.dcb.core.api.datavalidation.PatronRequestCommand;
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
public class PatronRequestController  {

	public static final Logger log = LoggerFactory.getLogger(PatronRequestController.class);

	private final PatronRequestService patronRequestService;

	public PatronRequestController(PatronRequestService patronRequestService) {
		this.patronRequestService = patronRequestService;
	}

	@SingleResult
	@Post("/patrons/requests/place")
	public Mono<HttpResponse<PatronRequestCommand>> placePatronRequest(
		@Body @Valid Mono<PatronRequestCommand> patronRequestCommand) {

		log.debug("REST, place patron request: {}", patronRequestCommand);

		return patronRequestService.savePatronRequest(patronRequestCommand)
			.map(HttpResponse::ok);
	}
}
