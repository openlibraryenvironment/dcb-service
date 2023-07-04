package org.olf.dcb.core.api;

import static io.micronaut.http.MediaType.APPLICATION_JSON;

import javax.validation.Valid;

import io.micronaut.security.authentication.Authentication;

import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.request.fulfilment.PatronRequestService;
import org.olf.dcb.request.fulfilment.PatronService;
import org.olf.dcb.request.fulfilment.PlacePatronRequestCommand;
import org.olf.dcb.storage.PatronRequestRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
import io.micronaut.validation.Validated;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import reactor.core.publisher.Mono;
import java.util.Map;

@Validated
@Secured(SecurityRule.IS_ANONYMOUS)
@Controller("/patrons/requests")
@Tag(name = "Patron Request API")
public class PatronRequestController {
	private static final Logger log = LoggerFactory.getLogger(PatronRequestController.class);

	private final PatronRequestService patronRequestService;
	private final PatronRequestRepository patronRequestRepository;
	private final PatronService patronService;

	public PatronRequestController(PatronRequestService patronRequestService,
		PatronRequestRepository patronRequestRepository, PatronService patronService) {
		this.patronRequestService = patronRequestService;
		this.patronRequestRepository = patronRequestRepository;
		this.patronService = patronService;
	}

	/**
	 * ToDo: This method should be secured with IS_AUTHENTICATED as the list method below
	 * @param command - patron request view - passed in params should match claims in the incoming JWT
	 *                to prevent a user from using their creds to place a request against another user acct
	 * @return
	 */
	@SingleResult
	@Post(value = "/place", consumes = APPLICATION_JSON)
	public Mono<MutableHttpResponse<PatronRequestView>> placePatronRequest(
		@Body @Valid PlacePatronRequestCommand command) {

		log.debug("REST, place patron request: {}", command);

		return patronRequestService.placePatronRequest(command)
			.map(PatronRequestView::from)
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
	@Get("/{?pageable*}")
	public Mono<Page<PatronRequest>> list(@Parameter(hidden = true) @Valid Pageable pageable,
																				Authentication authentication) {

		Map<String,Object> claims = authentication.getAttributes();
		log.info("list requests for {}",claims);
		Object patron_home_system = claims.get("localSystemCode");
		Object patron_home_id = claims.get("localSystemPatronId");

		if (pageable == null) {
			pageable = Pageable.from(0, 100);
		}

		if ( ( patron_home_system != null ) && ( patron_home_id != null ) ) {
                        log.debug("Finding requests for {} {}",patron_home_system,patron_home_id);
			return Mono.from(patronRequestRepository.findRequestsForPatron(patron_home_system.toString(), patron_home_id.toString(), pageable));
		}
		else {
                        log.debug("Missing values for patron requests");
			return Mono.empty();
		}
	}

}
