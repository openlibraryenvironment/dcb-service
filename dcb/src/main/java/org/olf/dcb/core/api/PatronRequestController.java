package org.olf.dcb.core.api;

import io.micronaut.core.annotation.Nullable;
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
import org.olf.dcb.core.api.discovery.PatronStatusMapper;
import org.olf.dcb.core.api.serde.PatronRequestSummary;
import org.olf.dcb.core.api.serde.RequestedTitleStat;
import org.olf.dcb.core.api.serde.TopRequestorStat;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.request.fulfilment.FailedPreflightCheck;
import org.olf.dcb.request.fulfilment.PatronRequestService;
import org.olf.dcb.request.fulfilment.PlacePatronRequestCommand;
import org.olf.dcb.request.fulfilment.PreflightCheckFailedException;
import org.olf.dcb.request.fulfilment.WalkUpRequestCommand;
import org.olf.dcb.request.workflow.CleanupPatronRequestTransition;
import org.olf.dcb.request.workflow.PatronRequestWorkflowService;
import org.olf.dcb.storage.PatronRequestRepository;
import org.olf.dcb.tracking.TrackingService;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.function.TupleUtils;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.micronaut.http.HttpResponse.badRequest;
import static io.micronaut.http.MediaType.APPLICATION_JSON;
import static org.olf.dcb.security.RoleNames.ADMINISTRATOR;
import static org.olf.dcb.security.RoleNames.CONSORTIUM_ADMIN;
import static org.olf.dcb.security.RoleNames.LIBRARY_ADMIN;
import static org.olf.dcb.security.RoleNames.LIBRARY_READ_ONLY;

import org.olf.dcb.security.RoleNames;

/**
 * The STAFF and SERVICE surface for patron requests. Default-DENY.
 *
 * This controller places holds, checks items out and mutates the state machine. It
 * previously defaulted to IS_AUTHENTICATED, which is not a permission but a claim
 * about the whole Keycloak realm — "every principal this realm will ever
 * authenticate may place a hold as an arbitrary patron". That invariant is
 * maintained in Keycloak config by people who never read this file, and it broke
 * the moment discovery credentials joined the realm: /place, /place/walkup,
 * /place/expeditedCheckout, /rollback and /update were all reachable by any
 * authenticated principal, including one held by a patron's browser.
 *
 * Every method is now gated by an explicit role set, either by this default or by
 * its own @Secured. The patron-facing surface lives on
 * {@link org.olf.dcb.core.api.discovery.DiscoveryPatronRequestsController}, and
 * DISCOVERY_SERVICE must never appear here.
 */
@Controller("/patrons/requests")
@Validated
@Secured({CONSORTIUM_ADMIN, ADMINISTRATOR, LIBRARY_ADMIN, RoleNames.INTERNAL_API})
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

	/**
	 * LIBRARY_READ_ONLY is included on the three placement routes DELIBERATELY, and the
	 * role name is the reason it needs saying: it means read-only CONFIGURATION, not
	 * read-only circulation. dcb-admin-for-libraries confines those users to
	 * /requesting/*, which is precisely where staff requesting, walk-ups and expedited
	 * checkout live, so front-desk staff hold this role and place requests all day.
	 * Excluding it here 403s them.
	 *
	 * It is NOT on update/rollback/cleanup: those rewrite request state, are reached
	 * from /patronRequests/* which the same UI blocks for this role, and are genuinely
	 * not read-only work.
	 */
	@Secured({CONSORTIUM_ADMIN, ADMINISTRATOR, LIBRARY_ADMIN, LIBRARY_READ_ONLY,
		RoleNames.INTERNAL_API})
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
	@Secured({CONSORTIUM_ADMIN, ADMINISTRATOR, LIBRARY_ADMIN, LIBRARY_READ_ONLY,
		RoleNames.INTERNAL_API})
	@SingleResult
	@Post(value = "/place/expeditedCheckout", consumes = APPLICATION_JSON)
	public Mono<PatronRequestView> placePatronRequestExpeditedCheckout(
		@Body @Valid PlacePatronRequestCommand command) {

		log.info("REST, place patron request with expedited checkout: {}", command);

		return patronRequestService.placePatronRequestExpeditedCheckout(command)
			.map(PatronRequestView::from);
	}

	/**
	 * A new version of walk-up requesting using the item barcode.
	 * Separate API for now as this is in preview.
	 */
	@Secured({CONSORTIUM_ADMIN, ADMINISTRATOR, LIBRARY_ADMIN, LIBRARY_READ_ONLY,
		RoleNames.INTERNAL_API})
	@SingleResult
	@Post(value = "/place/walkup", consumes = APPLICATION_JSON)
	public Mono<PatronRequestView> placeWalkUpRequest(
		@Body @Valid WalkUpRequestCommand command) {

		log.debug("REST, place walk-up request for barcode {} at {}", command.getItemBarcode(), command.getItemHostLmsCode());
		return patronRequestService.placeWalkUpRequest(command)
			.map(PatronRequestView::from);
	}

	// At first, this should only be for consortial administrators or other admin users.
	// Gets requests by patron barcode and has modes for 'active' and 'all time'
	// Useful for consortium admins and possibly also discovery services.
	@Secured({CONSORTIUM_ADMIN, ADMINISTRATOR})
	@Operation(
		summary = "List Requests by Patron Barcode",
		description = "Returns a list of patron requests associated with a specific barcode and Host LMS."
	)
	@Get(value = "/{hostLmsCode}")
	public Flux<PatronRequestSummary> getPatronRequestsByBarcode(
		@Parameter(description = "The Host LMS Code") String hostLmsCode,
		@Parameter(description = "The Patron's Barcode") @QueryValue String barcode,
		@Parameter(description = "Filter mode: 'active' (default) or 'all'") @QueryValue(defaultValue = "active") @Nullable String mode) {

		return requestsForPatronBarcode(hostLmsCode, barcode, mode);
	}

	/**
	 * LEGACY. The path this route shipped on, and the one EBSCO Locate calls in
	 * production with its ADMIN service credential. Locate is closed-source and outside
	 * this workspace, so the rename to /patrons/requests/{hostLmsCode} would have 404'd a
	 * live integration; the rename was cosmetic, the security work was the role set.
	 *
	 * A SEPARATE METHOD rather than a second uri on the one above, because @Secured is
	 * per-method: the exception is for an ADMIN service credential, so this alias admits
	 * ADMINISTRATOR and nothing else. CONSORTIUM_ADMIN reached the old path historically
	 * and has no caller that needs it -- it uses the current path like everyone else.
	 *
	 * Delete when EBSCO confirms it has moved. See
	 * docs/discovery-service-approach.md section 8.
	 */
	@Secured(ADMINISTRATOR)
	@Operation(
		summary = "List Requests by Patron Barcode (legacy path)",
		description = "Deprecated alias of GET /patrons/requests/{hostLmsCode}, retained for an "
			+ "existing integration. Use the current path."
	)
	@Get(value = "/patrons/{hostLmsCode}/requests")
	public Flux<PatronRequestSummary> getPatronRequestsByBarcodeLegacyPath(
		@Parameter(description = "The Host LMS Code") String hostLmsCode,
		@Parameter(description = "The Patron's Barcode") @QueryValue String barcode,
		@Parameter(description = "Filter mode: 'active' (default) or 'all'") @QueryValue(defaultValue = "active") @Nullable String mode) {

		return requestsForPatronBarcode(hostLmsCode, barcode, mode);
	}

	private Flux<PatronRequestSummary> requestsForPatronBarcode(String hostLmsCode, String barcode,
		String mode) {

		final var rawRequests = "all".equalsIgnoreCase(mode)
			? patronRequestRepository.findAllRequestsForPatronByBarcode(hostLmsCode, barcode)
			: patronRequestRepository.findActiveRequestsForPatronByBarcode(hostLmsCode, barcode);

		// Staff enrichment: errorMessage is retained here because a librarian can act
		// on it. The patron-facing path deliberately drops it.
		return rawRequests.map(PatronStatusMapper::enrichForStaff);
	}

	@Error
	public HttpResponse<ChecksFailure> onCheckFailure(PreflightCheckFailedException exception) {
		return badRequest(ChecksFailure.builder()
			.failedChecks(exception.getFailedChecks())
			.build());
	}

	// The patron-facing "my requests" list and patron-initiated cancellation used to
	// live here, gated on a PATRON role. Both moved to
	// org.olf.dcb.core.api.discovery.DiscoveryPatronRequestsController: on a
	// controller whose class default is a staff role set, a patron-shaped endpoint is
	// one forgotten annotation away from granting patrons everything else in the file.

	/**
	 * LEGACY. Retained for EBSCO Locate, which calls this in production with an ADMIN
	 * service credential and cannot be changed -- it is closed-source and outside this
	 * workspace, so the compatibility audit that cleared the admin UIs could not see it.
	 *
	 * Do not build anything new on this. It self-scopes off localSystemCode /
	 * localSystemPatronId claims ON THE CALLER'S OWN TOKEN, which is the confused-deputy
	 * shape the discovery work exists to replace: the caller's credential decides which
	 * patron's requests come back. It is tolerable here ONLY because ADMINISTRATOR can
	 * already read every request in the consortium through the barcode route and GraphQL,
	 * so the claims lookup grants no authority the role does not already have.
	 *
	 * A service credential carries no patron claims, so for one it returns an empty page
	 * rather than failing -- which is exactly what it did before this branch, and is why
	 * this must NOT be "fixed" into returning everything.
	 *
	 * The replacement is GET /discovery/requests, where the patron is a verified
	 * assertion rather than a claim on the caller's own token. Delete this when EBSCO
	 * has moved. See docs/discovery-service-approach.md section 8.
	 */
	@Secured(ADMINISTRATOR)
	@Operation(summary = "Browse Requests", description = "Paginate through the list of Patron Requests", parameters = {
			@Parameter(in = ParameterIn.QUERY, name = "number", description = "The page number", schema = @Schema(type = "integer", format = "int32"), example = "1"),
			@Parameter(in = ParameterIn.QUERY, name = "size", description = "The page size", schema = @Schema(type = "integer", format = "int32"), example = "100") })
	@Get("/{?pageable*}")
	public Mono<Page<PatronRequest>> list(@Parameter(hidden = true) @Valid Pageable pageable,
			Authentication authentication) {

		Map<String, Object> claims = authentication.getAttributes();
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
			// No patron claims on the caller's token: an empty page, as before. Not an
			// error, and deliberately not "everything".
			log.debug("No patron claims on the calling token; returning an empty page");
			return Mono.empty();
		}
	}

	@Secured({CONSORTIUM_ADMIN, LIBRARY_ADMIN, ADMINISTRATOR})
	@Get("/stats/top-requestors")
	public Mono<Page<TopRequestorStat>> getTopRequestors(
		@Nullable @QueryValue String libraryCode,
		Pageable pageable) {

		return Mono.from(patronRequestRepository.findTopRequestors(libraryCode, pageable));
	}

	@Secured({CONSORTIUM_ADMIN, LIBRARY_ADMIN, ADMINISTRATOR})
	@Get("/stats/top-requested-titles")
	public Mono<Page<RequestedTitleStat>> getMostRequestedTitles(
		@Nullable @QueryValue Instant startDate,
		@Nullable @QueryValue Instant endDate,
		@Nullable @QueryValue String libraryCode,
		Pageable pageable) {

		return Mono.from(patronRequestRepository.findMostRequestedTitles(startDate, endDate, libraryCode, pageable));
	}

	@Value
	@Serdeable
	@Builder
	public static class ChecksFailure {
		List<FailedPreflightCheck> failedChecks;
	}
}
