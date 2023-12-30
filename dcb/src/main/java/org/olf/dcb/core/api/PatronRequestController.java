package org.olf.dcb.core.api;

import static io.micronaut.http.HttpResponse.badRequest;
import static io.micronaut.http.MediaType.APPLICATION_JSON;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.PatronRequest.Status;
import org.olf.dcb.request.fulfilment.FailedPreflightCheck;
import org.olf.dcb.request.fulfilment.PreflightCheckFailedException;
import org.olf.dcb.request.fulfilment.PatronRequestService;
import org.olf.dcb.request.fulfilment.PlacePatronRequestCommand;
import org.olf.dcb.request.workflow.CleanupPatronRequestTransition;
import org.olf.dcb.request.workflow.PatronRequestStateTransition;
import org.olf.dcb.request.workflow.PatronRequestWorkflowService;
import org.olf.dcb.storage.PatronRequestRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Error;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.authentication.Authentication;
import io.micronaut.security.rules.SecurityRule;
import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.validation.Validated;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Value;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.function.TupleUtils;

@Validated
@Secured(SecurityRule.IS_AUTHENTICATED)
@Controller("/patrons/requests")
@Tag(name = "Patron Request API")
public class PatronRequestController {
	private static final Logger log = LoggerFactory.getLogger(PatronRequestController.class);

	private final PatronRequestService patronRequestService;
	private final PatronRequestRepository patronRequestRepository;
	private final PatronRequestWorkflowService workflowService;
	private final CleanupPatronRequestTransition cleanupPatronRequestTransition;

	public PatronRequestController(PatronRequestService patronRequestService,
			PatronRequestRepository patronRequestRepository, 
			PatronRequestWorkflowService workflowService,
			CleanupPatronRequestTransition cleanupPatronRequestTransition) {
		this.patronRequestService = patronRequestService;
		this.patronRequestRepository = patronRequestRepository;
		this.workflowService = workflowService;
		this.cleanupPatronRequestTransition = cleanupPatronRequestTransition;
	}
	
	public PatronRequest ensureValidStateForTransition( final PatronRequest patronRequest ) {
		return switch (patronRequest.getStatus()) {
			// we want to be able to clean up errored requests, so am removing this for now
			// case ERROR -> throw new IllegalStateException("Cannot transition errored requests");
			case CANCELLED -> throw new IllegalStateException("Cannot transition cancelled requests");
			
			default -> patronRequest;
		};
	}
	
	private Mono<PatronRequestStateTransition> resolvePatronRequestTransition( PatronRequest patronRequest, Predicate<PatronRequestStateTransition> predicate ) {
		
		return Flux.fromStream( workflowService.getPossibleStateTransitionsFor(patronRequest) )
				.filter( predicate )
				.next();
	}
	
	@SingleResult
	@Post(value = "/{patronRequestId}/transtion/{status}", consumes = APPLICATION_JSON)
	public Mono<PatronRequest> transitionPatronRequest(@NotNull final UUID patronRequestId, @NotNull Status status) {
				
		return patronRequestService
			.findById( patronRequestId )
			.map( this::ensureValidStateForTransition )
			.zipWhen( (req) -> resolvePatronRequestTransition(req,
					transition -> transition.getTargetStatus()
						.orElse(status) == status ))
			
			.flatMapMany( TupleUtils.function(workflowService::progressUsing ))
			.last();
	}
	
	/**
	 * Special state transitions that don't have a target state i.e. they leave the state untouched, but
	 * with a workflow associated should be listed explicitly as url entry points
	 * 
	 * TODO: We prolly want to change this, to not be so explicit. But I think that's part of a necessary
	 * overhaul to the whole system.
	 */
	@SingleResult
	@Post(value = "/{patronRequestId}/transition/cleanup", consumes = APPLICATION_JSON)
	public Mono<PatronRequest> cleanupPatronRequest(@NotNull final UUID patronRequestId) {

		log.info("Request cleanup for {}",patronRequestId);

		return patronRequestService
			.findById( patronRequestId )
			.map( this::ensureValidStateForTransition )
			.zipWhen( (req) -> Mono.just(cleanupPatronRequestTransition))
			.flatMapMany( TupleUtils.function(workflowService::progressUsing ))
			.doOnError(error -> log.error("Problem attempting to clean up request",error))
		.last();
	}


	@SingleResult
	@Post(value = "/place", consumes = APPLICATION_JSON)
	public Mono<PatronRequestView> placePatronRequest(
			@Body @Valid PlacePatronRequestCommand command) {

		log.info("REST, place patron request: {}", command);

		return patronRequestService.placePatronRequest(command)
			.map(PatronRequestView::from);
	}

	@Error
	public HttpResponse<ChecksFailure> onCheckFailure(PreflightCheckFailedException exception) {
		return badRequest(ChecksFailure.builder()
			.failedChecks(exception.getFailedChecks())
			.build());
	}

	@Secured(SecurityRule.IS_AUTHENTICATED)
	@Operation(summary = "Browse Requests", description = "Paginate through the list of Patron Requests", parameters = {
			@Parameter(in = ParameterIn.QUERY, name = "number", description = "The page number", schema = @Schema(type = "integer", format = "int32"), example = "1"),
			@Parameter(in = ParameterIn.QUERY, name = "size", description = "The page size", schema = @Schema(type = "integer", format = "int32"), example = "100") })
	@Get("/{?pageable*}")
	public Mono<Page<PatronRequest>> list(@Parameter(hidden = true) @Valid Pageable pageable,
			Authentication authentication) {

		Map<String, Object> claims = authentication.getAttributes();
		log.info("list requests for {}", claims);
		Object patron_home_system = claims.get("localSystemCode");
		Object patron_home_id = claims.get("localSystemPatronId");

		if (pageable == null) {
			pageable = Pageable.from(0, 100);
		}

		if ((patron_home_system != null) && (patron_home_id != null)) {
			log.debug("Finding requests for {} {}", patron_home_system, patron_home_id);
			return Mono.from(patronRequestRepository.findRequestsForPatron(patron_home_system.toString(),
					patron_home_id.toString(), pageable));
		} else {
			log.debug("Missing values for patron requests");
			return Mono.empty();
		}
	}

	@Value
	@Serdeable
	@Builder
	public static class ChecksFailure {
		List<FailedPreflightCheck> failedChecks;
	}
}
