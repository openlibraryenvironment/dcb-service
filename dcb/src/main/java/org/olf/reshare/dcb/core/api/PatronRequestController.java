package org.olf.reshare.dcb.core.api;

import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.*;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.olf.reshare.dcb.core.api.datavalidation.PatronRequestCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static io.micronaut.http.MediaType.APPLICATION_JSON;


@Consumes(APPLICATION_JSON)
@Secured(SecurityRule.IS_ANONYMOUS)
@Controller
@Tag(name = "Patron Request API")
public class PatronRequestController  {

	public static final Logger log = LoggerFactory.getLogger(PatronRequestController.class);

	private final PatronRequestRepository patronRequestRepository;

	public PatronRequestController(PatronRequestRepository patronRequestRepository) {
		this.patronRequestRepository = patronRequestRepository;
	}
	Map<String, Object> inMemoryDatastore = new ConcurrentHashMap<>();

	@SingleResult
	@Status(HttpStatus.OK)
	@Post("/patrons/requests/place")
	public Mono<HttpResponse<PatronRequestCommand>> placePatronRequest(@Body Mono<PatronRequestCommand> patronRequestCommand) {
		log.debug("REST, place patron request: {}", patronRequestCommand);

		inMemoryDatastore.put("patronRequestCommand", patronRequestCommand);
		return patronRequestCommand.map(p -> {
				inMemoryDatastore.put("patronRequestCommand", p);
				return HttpResponse.ok(p);
			}
		);
	}


}
