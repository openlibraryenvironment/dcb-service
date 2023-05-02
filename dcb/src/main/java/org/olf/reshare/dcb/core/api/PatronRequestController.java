package org.olf.reshare.dcb.core.api;

import static io.micronaut.http.MediaType.APPLICATION_JSON;

import javax.validation.Valid;

import io.micronaut.http.MutableHttpResponse;
import org.olf.reshare.dcb.request.fulfilment.PatronRequestService;
import org.olf.reshare.dcb.request.fulfilment.PatronService;
import org.olf.reshare.dcb.request.fulfilment.PlacePatronRequestCommand;
import org.olf.reshare.dcb.core.model.PatronRequest;
import org.olf.reshare.dcb.storage.PatronRequestRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.http.HttpResponse;
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

	@SingleResult
	@Post(value = "/place", consumes = APPLICATION_JSON)
	public Mono<MutableHttpResponse<PatronRequestView>> placePatronRequest(
		@Body @Valid PlacePatronRequestCommand command) {

		log.debug("REST, place patron request: {}", command);

		return patronRequestService.placePatronRequest(command)
			.flatMap(this::toPatronRequestView)
			.map(HttpResponse::ok);
	}

	private Mono<PatronRequestView> toPatronRequestView(PatronRequest patronRequest) {
		log.debug("toPatronRequestView({})", patronRequest);
		return patronService.addPatronIdentitiesAndHostLms(patronRequest)
			.map(PatronRequestView::from);
	}

	@Secured(SecurityRule.IS_ANONYMOUS)
        @Operation(
                summary = "Browse Requests",
                description = "Paginate through the list of Patron Requests",
                parameters = {
                        @Parameter(in = ParameterIn.QUERY, name = "number", description = "The page number", schema = @Schema(type = "integer", format = "int32"), example = "1"),
                        @Parameter(in = ParameterIn.QUERY, name = "size", description = "The page size", schema = @Schema(type = "integer", format = "int32"), example = "100")}
        )
        @Get("/{?pageable*}")
        public Mono<Page<PatronRequest>> list(@Parameter(hidden = true) @Valid Pageable pageable) {
                if (pageable == null) {
                        pageable = Pageable.from(0, 100);
                }

                return Mono.from(patronRequestRepository.findAll(pageable));
        }

}
