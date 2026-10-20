package org.olf.dcb.core.api.discovery;

import static io.micronaut.http.MediaType.APPLICATION_JSON;
import static org.olf.dcb.security.RoleNames.DISCOVERY_SERVICE;

import java.util.HashMap;
import java.util.Optional;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import org.olf.dcb.core.api.PatronRequestController;
import org.olf.dcb.core.api.PatronRequestView;
import org.olf.dcb.core.api.serde.PatronRequestSummary;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.request.fulfilment.PatronRequestAuditService;
import org.olf.dcb.request.fulfilment.PatronRequestCancellationService;
import org.olf.dcb.request.fulfilment.PreflightCheckFailedException;
import org.olf.dcb.request.fulfilment.PatronRequestService;
import org.olf.dcb.request.fulfilment.PlacePatronRequestCommand;
import org.olf.dcb.security.discovery.DiscoveryConfig;
import org.olf.dcb.security.discovery.PatronAssertion;
import org.olf.dcb.security.discovery.PatronAssertionVerifier;
import org.olf.dcb.storage.PatronRequestRepository;
import org.olf.dcb.tracking.TrackingService;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Error;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.security.annotation.Secured;
import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.validation.Validated;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.olf.dcb.core.svc.AgencyService;
import reactor.core.publisher.Mono;

/**
 * The patron-request surface for a DISCOVERY_SERVICE credential; every route under /discovery/
 * is held to its role by ApiSecurityArchitectureTests.
 *
 * Every method derives the patron from a signed assertion DCB verified
 * ({@link PatronAssertionVerifier}) — never from a path variable, query parameter
 * or request body. A discovery service therefore cannot act on a patron it has not
 * vouched for, and DCB — not the caller — remains the place where "is this your
 * request?" is answered.
 *
 * DO NOT add a method here that takes a patron identity from the request, and do
 * not add DISCOVERY_SERVICE to any @Secured outside this package. If a discovery
 * service needs something staff-shaped, it needs a staff role and a different
 * controller.
 */
@Controller("/discovery/requests")
@Validated
@Secured(DISCOVERY_SERVICE)
@Tag(name = "Discovery API")
@Slf4j
// Assertion verification is synchronous and may refresh trusted JWKS over bounded HTTP.
@ExecuteOn(TaskExecutors.BLOCKING)
@Requires(property = DiscoveryConfig.ENABLED, value = "true")
public class DiscoveryPatronRequestsController {

	private static final String ASSERTION_DESCRIPTION =
		"Short-lived patron assertion signed by the discovery service. Identifies the patron; "
			+ "the Authorization bearer identifies the calling service.";

	private final PatronRequestRepository patronRequestRepository;
	private final PatronRequestService patronRequestService;
	private final PatronRequestCancellationService cancellationService;
	private final PatronAssertionVerifier assertionVerifier;
	private final TrackingService trackingService;
	private final PatronRequestAuditService patronRequestAuditService;
	private final AgencyService agencyService;

	public DiscoveryPatronRequestsController(PatronRequestRepository patronRequestRepository,
		PatronRequestService patronRequestService,
		PatronRequestCancellationService cancellationService,
		PatronAssertionVerifier assertionVerifier, TrackingService trackingService,
		PatronRequestAuditService patronRequestAuditService,
		AgencyService agencyService) {

		this.patronRequestRepository = patronRequestRepository;
		this.patronRequestService = patronRequestService;
		this.cancellationService = cancellationService;
		this.assertionVerifier = assertionVerifier;
		this.trackingService = trackingService;
		this.patronRequestAuditService = patronRequestAuditService;
		this.agencyService = agencyService;
	}

	@Operation(summary = "The asserted patron's requests",
		description = "Requests belonging to the patron named in the X-OpenRS-Patron-Assertion "
			+ "header, in the patron-facing status vocabulary. Internal error detail is "
			+ "deliberately omitted; use discoveryStatus and statusDescription.",
		parameters = {
			@Parameter(in = ParameterIn.QUERY, name = "number", description = "The page number",
				schema = @Schema(type = "integer", format = "int32"), example = "0"),
			@Parameter(in = ParameterIn.QUERY, name = "size", description = "The page size",
				schema = @Schema(type = "integer", format = "int32"), example = "100"),
			@Parameter(in = ParameterIn.HEADER, name = PatronAssertionVerifier.ASSERTION_HEADER,
				required = true, description = ASSERTION_DESCRIPTION) })
	@Get("/{?pageable*}")
	public Mono<Page<PatronRequestSummary>> listOwnRequests(
		@Parameter(hidden = true) @Valid @Nullable Pageable pageable,
		HttpRequest<?> request) {

		final var patron = assertionVerifier.verify(request);
		final var page = pageable != null ? pageable : Pageable.from(0, 100);

		return Mono.from(patronRequestRepository.findSummariesForPatron(
				patron.systemCode(), patron.patronId(), page))
			.map(result -> result.map(PatronStatusMapper::enrichForPatron));
	}

	/**
	 * Place a request AS the asserted patron.
	 *
	 * The requestor is built here from the verified assertion. The body carries only
	 * what the patron actually chose — what to request, where to collect it — and
	 * carries no identity at all. That is the whole point: on the staff endpoint the
	 * requestor comes from the request body, so a caller can place a request as
	 * anybody; here the identity is not the caller's to state.
	 */
	@SingleResult
	@Operation(summary = "Place a request as the asserted patron",
		description = "The requestor is derived from the verified patron assertion, never from "
			+ "the body. Preflight failures come back as the standard failedChecks response.",
		parameters = @Parameter(in = ParameterIn.HEADER,
			name = PatronAssertionVerifier.ASSERTION_HEADER,
			required = true, description = ASSERTION_DESCRIPTION))
	@Post(value = "/place", consumes = APPLICATION_JSON)
	public Mono<PatronRequestView> placeOwnRequest(
		@Body @Valid DiscoveryPlaceRequest body,
		HttpRequest<?> request) {

		final var patron = assertionVerifier.verify(request);

		final var copy = chosenCopy(body.itemLocalId(), body.itemHostLmsCode(), body.itemAgencyCode());

		log.info("Discovery place request for patron {}@{} asserted by {}, {}",
			patron.patronId(), patron.systemCode(), patron.assertingService(),
			copy == null ? "copy chosen by DCB" : "copy " + copy.getLocalId() + "@" + copy.getLocalSystemCode());

		return patronAgency(patron, body.agencyCode()).flatMap(agency ->
			patronRequestService.placePatronRequest(PlacePatronRequestCommand.builder()
				.citation(PlacePatronRequestCommand.Citation.builder()
					.bibClusterId(body.bibClusterId())
					.volumeDesignator(body.volumeDesignator())
					.build())
				.pickupLocation(PlacePatronRequestCommand.PickupLocation.builder()
					.code(body.pickupLocationCode())
					.build())
				// Identity from the assertion DCB verified — NOT from the body.
				.requestor(PlacePatronRequestCommand.Requestor.builder()
					.localId(patron.patronId())
					.localSystemCode(patron.systemCode())
					.homeLibraryCode(body.homeLibraryCode())
					.agencyCode(agency.orElse(null))
					.build())
				.requesterNote(body.requesterNote())
				.item(copy)
				.build()))
			.flatMap(placed -> auditPlacement(placed, patron, "Patron placed a request via discovery"))
			.map(PatronRequestView::from);
	}

	/**
	 * The copy a patron chose, or null to let DCB choose. PatronRequestService drops an
	 * incomplete item and resolves one itself, so an incomplete choice is refused here rather
	 * than silently becoming a different copy.
	 */
	@Nullable
	static PlacePatronRequestCommand.Item chosenCopy(@Nullable String localId,
		@Nullable String hostLmsCode, @Nullable String agencyCode) {

		final long given = Stream.of(localId, hostLmsCode, agencyCode)
			.filter(value -> value != null && !value.isBlank())
			.count();

		if (given == 0) {
			return null;
		}
		if (given < 3) {
			throw new HttpStatusException(HttpStatus.BAD_REQUEST,
				"A chosen copy needs its local id, Host LMS code and agency code");
		}
		return PlacePatronRequestCommand.Item.builder()
			.localId(localId)
			.localSystemCode(hostLmsCode)
			.agencyCode(agencyCode)
			.build();
	}

	/**
	 * What a patron chose. Deliberately has no requestor: a discovery service cannot
	 * name the patron it is requesting for, only assert it and be verified.
	 */
	@Serdeable
	public record DiscoveryPlaceRequest(
		@NotNull UUID bibClusterId,
		@NotNull String pickupLocationCode,
		@Nullable String volumeDesignator,
		// Hints DCB may resolve for itself; they carry no authority.
		@Nullable String homeLibraryCode,
		@Nullable String agencyCode,
		@Nullable String requesterNote,
		// A specific copy, all three or none: none leaves the choice to the item resolver.
		@Nullable String itemLocalId,
		@Nullable String itemHostLmsCode,
		@Nullable String itemAgencyCode) {
	}

	/**
	 * Walk-up: check out to the asserted patron the copy they are holding. This creates a LOAN,
	 * with the walk-up refusals in {@link PatronRequestService#placeDiscoveryWalkUp}.
	 *
	 * Accepted deliberately: a compromised discovery service key can now cause loans, not only
	 * holds. DCB cannot prove the patron is at the desk; the trusted service attests that.
	 */
	@SingleResult
	@Operation(summary = "Check an item out to the asserted patron (walk-up)",
		description = "Creates a loan immediately. The borrower is derived from the verified "
			+ "patron assertion, never from the body; the body names the item the patron is "
			+ "holding and the library they are in.",
		parameters = @Parameter(in = ParameterIn.HEADER,
			name = PatronAssertionVerifier.ASSERTION_HEADER,
			required = true, description = ASSERTION_DESCRIPTION))
	@Post(value = "/place/expedited", consumes = APPLICATION_JSON)
	public Mono<PatronRequestView> placeOwnExpeditedCheckout(
		@Body @Valid DiscoveryExpeditedRequest body,
		HttpRequest<?> request) {

		final var patron = assertionVerifier.verify(request);

		// Logged because a loan created without a member of staff present is the entry an
		// auditor will look for. The asserting service is named: "who lent this" has an
		// answer that is not "the system".
		log.info("Discovery walk-up checkout for patron {}@{} of item {}@{}, asserted by {}",
			patron.patronId(), patron.systemCode(), body.itemLocalId(),
			body.itemHostLmsCode(), patron.assertingService());

		return patronAgency(patron, body.agencyCode()).flatMap(agency ->
			patronRequestService.placeDiscoveryWalkUp(
				PlacePatronRequestCommand.builder()
					.citation(PlacePatronRequestCommand.Citation.builder()
						.bibClusterId(body.bibClusterId())
						.volumeDesignator(body.volumeDesignator())
						.build())
					// For a walk-up the pickup location IS where the patron is standing.
					.pickupLocation(PlacePatronRequestCommand.PickupLocation.builder()
						.code(body.pickupLocationCode())
						.build())
					// Identity from the assertion DCB verified — NOT from the body.
					.requestor(PlacePatronRequestCommand.Requestor.builder()
						.localId(patron.patronId())
						.localSystemCode(patron.systemCode())
						.homeLibraryCode(body.homeLibraryCode())
						.agencyCode(agency.orElse(null))
						.build())
					// Without its agency the item was dropped and DCB chose another copy -
					// for a walk-up, not the one in the patron's hands.
					.item(chosenCopy(body.itemLocalId(), body.itemHostLmsCode(), body.itemAgencyCode()))
					.isExpeditedRequest(true)
					.build()))
			.flatMap(placed -> auditPlacement(placed, patron,
				"Patron checked an item out via discovery (walk-up)"))
			.map(PatronRequestView::from);
	}

	private Mono<PatronRequest> auditPlacement(PatronRequest placed, PatronAssertion patron,
		String message) {

		final var auditData = new HashMap<String, Object>();
		auditData.put("initiatedByPatronId", patron.patronId());
		auditData.put("patronHostLmsCode", patron.systemCode());
		auditData.put("assertedByDiscoveryService", patron.assertingService());

		return patronRequestAuditService.addAuditEntry(placed, message, auditData)
			.thenReturn(placed)
			// The request exists by now: reporting a failed audit write as a failed placement
			// would have the patron place it again
			.onErrorResume(error -> {
				log.error("Could not audit discovery placement of request {}", placed.getId(), error);
				return Mono.just(placed);
			});
	}

	// A hint from the body, so it must at least be an agency on the patron's own library system
	private Mono<Optional<String>> patronAgency(PatronAssertion patron, @Nullable String hinted) {
		if (hinted == null || hinted.isBlank()) {
			return Mono.just(Optional.empty());
		}

		return agencyService.findByCode(hinted)
			.filter(agency -> agency.getHostLms() != null
				&& patron.systemCode().equals(agency.getHostLms().getCode()))
			.map(agency -> Optional.of(agency.getCode()))
			.switchIfEmpty(Mono.error(new HttpStatusException(HttpStatus.BAD_REQUEST,
				"agencyCode must name an agency on the patron's own library system")));
	}

	/**
	 * The item a patron is holding and the library they are in.
	 *
	 * As with {@link DiscoveryPlaceRequest}, deliberately no requestor: a discovery service
	 * cannot name the patron it is lending to, only assert it and be verified. The item
	 * fields are a CHOICE — DCB resolves them and applies the same preflight checks a staff
	 * expedited checkout gets.
	 */
	@Serdeable
	public record DiscoveryExpeditedRequest(
		@NotNull UUID bibClusterId,
		@NotNull String pickupLocationCode,
		@NotNull String itemLocalId,
		@NotNull String itemHostLmsCode,
		@Nullable String itemAgencyCode,
		@Nullable String volumeDesignator,
		// Hints DCB may resolve for itself; they carry no authority.
		@Nullable String homeLibraryCode,
		@Nullable String agencyCode) {
	}

	/**
	 * Patron-initiated cancellation, self-scoped to the asserted patron.
	 *
	 * Cancels the patron's own borrowing-side hold only. It does NOT transition the
	 * request: the forced tracking update lets the normal cancellation transition
	 * converge promptly instead of waiting for the next scheduled poll, and the
	 * state machine stays the sole owner of transitions. Hence 202 — CANCELLED lands
	 * asynchronously, never from this call.
	 *
	 * A request that is not the asserted patron's yields 404, indistinguishable from
	 * one that does not exist, so ids cannot be probed.
	 */
	@SingleResult
	@Operation(summary = "Cancel the asserted patron's own request",
		parameters = @Parameter(in = ParameterIn.HEADER,
			name = PatronAssertionVerifier.ASSERTION_HEADER,
			required = true, description = ASSERTION_DESCRIPTION))
	@Post(value = "/{patronRequestId}/cancel", consumes = APPLICATION_JSON)
	public Mono<HttpResponse<CancellationAccepted>> cancelOwnRequest(
		@NotNull final UUID patronRequestId,
		HttpRequest<?> request) {

		final PatronAssertion patron = assertionVerifier.verify(request);

		return cancellationService
			.cancelOwnRequest(patronRequestId, patron.systemCode(), patron.patronId(),
				patron.assertingService())
			.flatMap(result -> result.alreadyCancelled()
				// Idempotent replay: the hold is already gone. Do not poll the LMS again.
				? Mono.just(patronRequestId)
				: trackingService.forceUpdate(patronRequestId)
					// The hold IS cancelled; a slow poll must not turn success into a 500.
					.onErrorResume(error -> {
						log.warn("Post-cancellation tracking update failed for {}", patronRequestId, error);
						return Mono.just(patronRequestId);
					}))
			.map(id -> HttpResponse.accepted().body(new CancellationAccepted(id)));
	}

	/**
	 * Preflight rejections. @Error handlers are per-controller, not global, so without
	 * this a preflight failure on the discovery place endpoint would surface as a 500
	 * rather than the failedChecks body discovery clients already know how to translate.
	 * Same shape as PatronRequestController.onCheckFailure deliberately: one contract.
	 */
	@Error
	public HttpResponse<PatronRequestController.ChecksFailure> onCheckFailure(
		PreflightCheckFailedException exception) {

		return HttpResponse.badRequest(PatronRequestController.ChecksFailure.builder()
			.failedChecks(exception.getFailedChecks())
			.build());
	}

	@Error
	public HttpResponse<Map<String, String>> onInvalidAssertion(
		PatronAssertionVerifier.InvalidPatronAssertionException exception) {

		log.warn("Rejected discovery call: {}", exception.getMessage());

		return HttpResponse.<Map<String, String>>status(HttpStatus.UNAUTHORIZED)
			.body(Map.of("code", "INVALID_PATRON_ASSERTION",
				"message", "The patron assertion was not accepted."));
	}

	@Error
	public HttpResponse<Map<String, String>> onCancellationNotAllowed(
		PatronRequestCancellationService.CancellationNotAllowedException exception) {

		// Patron-facing vocabulary, not the state machine's. The raw status stays in
		// the log and the audit trail, where a librarian can act on it.
		return HttpResponse.<Map<String, String>>status(HttpStatus.CONFLICT)
			.body(Map.of(
				"code", "NOT_CANCELLABLE",
				"discoveryStatus", PatronStatusMapper.toDisplayStatus(exception.getStatus()).name(),
				"message", "This request can no longer be cancelled. Please see a librarian."));
	}

	@Error
	public HttpResponse<Map<String, String>> onCancellationNotConfirmed(
		PatronRequestCancellationService.CancellationFailedException exception) {

		// The borrowing system did not confirm it. Never report success for this.
		log.error("Patron cancellation not confirmed by {}", exception.getHostLmsCode());

		return HttpResponse.<Map<String, String>>status(HttpStatus.BAD_GATEWAY)
			.body(Map.of(
				"code", "CANCELLATION_NOT_CONFIRMED",
				"message", "We could not cancel this request automatically. Please see a librarian."));
	}

	@Serdeable
	public record CancellationAccepted(UUID id) {
	}
}
