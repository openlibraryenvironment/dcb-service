package org.olf.dcb.core.api;

import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Error;
import io.micronaut.http.annotation.*;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.authentication.Authentication;
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
import lombok.extern.slf4j.Slf4j;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.request.fulfilment.FailedPreflightCheck;
import org.olf.dcb.request.fulfilment.PatronRequestService;
import org.olf.dcb.request.fulfilment.PlacePatronRequestCommand;
import org.olf.dcb.request.fulfilment.PreflightCheckFailedException;
import org.olf.dcb.request.workflow.CleanupPatronRequestTransition;
import org.olf.dcb.request.workflow.PatronRequestWorkflowService;
import org.olf.dcb.security.RoleNames;
import org.olf.dcb.storage.PatronRequestRepository;
import org.olf.dcb.tracking.TrackingService;
import reactor.core.publisher.Mono;
import reactor.function.TupleUtils;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.micronaut.http.HttpResponse.badRequest;
import static io.micronaut.http.MediaType.APPLICATION_JSON;
import static io.micronaut.security.rules.SecurityRule.IS_AUTHENTICATED;
import static org.olf.dcb.security.RoleNames.ADMINISTRATOR;
import static org.olf.dcb.security.RoleNames.CONSORTIUM_ADMIN;
import static org.olf.dcb.security.RoleNames.LIBRARY_ADMIN;

@Controller("/patrons/requests")
@Validated
@Secured(IS_AUTHENTICATED)
@Tag(name = "Patron Request API")
@Slf4j
public class PatronRequestController {
	private final PatronRequestService patronRequestService;
	private final PatronRequestRepository patronRequestRepository;
	private final PatronRequestWorkflowService workflowService;
	private final CleanupPatronRequestTransition cleanupPatronRequestTransition;

	private final TrackingService trackingService;

	public PatronRequestController(PatronRequestService patronRequestService,
			PatronRequestRepository patronRequestRepository, 
			PatronRequestWorkflowService workflowService,
			CleanupPatronRequestTransition cleanupPatronRequestTransition,
			TrackingService trackingService) {

		this.patronRequestService = patronRequestService;
		this.patronRequestRepository = patronRequestRepository;
		this.workflowService = workflowService;
		this.cleanupPatronRequestTransition = cleanupPatronRequestTransition;
		this.trackingService = trackingService;
	}
	
	public PatronRequest ensureValidStateForTransition( final PatronRequest patronRequest ) {
		return switch (patronRequest.getStatus()) {
			// we want to be able to clean up errored requests, so am removing this for now
			// case ERROR -> throw new IllegalStateException("Cannot transition errored requests");
			case CANCELLED -> throw new IllegalStateException("Cannot transition cancelled requests");

			default -> patronRequest;
		};
	}

	/**
	 * Special state transitions that don't have a target state i.e. they leave the state untouched, but
	 * with a workflow associated should be listed explicitly as url entry points
	 * 
	 * TODO: We prolly want to change this, to not be so explicit. But I think that's part of a necessary
	 * overhaul to the whole system.
	 */
	@Secured({CONSORTIUM_ADMIN, ADMINISTRATOR, LIBRARY_ADMIN})
	@SingleResult
	@Post(value = "/{patronRequestId}/transition/cleanup", consumes = APPLICATION_JSON)
	public Mono<UUID> cleanupPatronRequest(@NotNull final UUID patronRequestId) {
		log.info("Request cleanup for {}",patronRequestId);

		return patronRequestService
			.findById( patronRequestId )
			.map( this::ensureValidStateForTransition )
			.zipWhen( (req) -> Mono.just(cleanupPatronRequestTransition))
			.flatMap( TupleUtils.function(workflowService::progressUsing )) // Note: progressUsing can return an empty mono
			.doOnSuccess(pr -> log.info("Successful cleanup for patron request {}", patronRequestId))
			.thenReturn(patronRequestId)
			.switchIfEmpty(Mono.defer(() -> {
				log.warn("Handling empty mono before clean up response :: pr {}", patronRequestId);
				return Mono.just(patronRequestId);
			}))
			.doOnError(error -> log.error("Problem attempting to clean up request",error));
	}

	/**
	 * Explicitly attempts to progress this request by polling downstream systems and then 
	 * looking for applicable transitions.
	 */
	@SingleResult
	@Post(value = "/{patronRequestId}/update", consumes = APPLICATION_JSON)
	public Mono<UUID> updatePatronRequest(@NotNull final UUID patronRequestId) {
		return trackingService.forceUpdate(patronRequestId);
	}

	/**
	 * Explicitly attempts to roll back this request by setting the previous status
	 */
	@SingleResult
	@Post(value = "/{patronRequestId}/rollback", consumes = APPLICATION_JSON)
	public Mono<UUID> rollbackPatronRequest(@NotNull final UUID patronRequestId) {
		return patronRequestService.initialiseRollback(patronRequestId);
	}

	@SingleResult
	@Post(value = "/place", consumes = APPLICATION_JSON)
	public Mono<PatronRequestView> placePatronRequest(
		@Body @Valid PlacePatronRequestCommand command) {

		log.info("REST, place patron request: {}", command);

		return patronRequestService.placePatronRequest(command)
			.map(PatronRequestView::from);
	}

	/**
	 * For situations such as on-site borrowing. Must include item due date in response.
	 */
	@SingleResult
	@Post(value = "/place/expeditedCheckout", consumes = APPLICATION_JSON)
	public Mono<PatronRequestView> placePatronRequestExpeditedCheckout(
		@Body @Valid PlacePatronRequestCommand command) {

		log.info("REST, place patron request with expedited checkout: {}", command);

		return patronRequestService.placePatronRequestExpeditedCheckout(command)
			.map(PatronRequestView::from);
	}

	@Error
	public HttpResponse<ChecksFailure> onCheckFailure(PreflightCheckFailedException exception) {
		return badRequest(ChecksFailure.builder()
			.failedChecks(exception.getFailedChecks())
			.build());
	}

	@Secured(ADMINISTRATOR)
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
