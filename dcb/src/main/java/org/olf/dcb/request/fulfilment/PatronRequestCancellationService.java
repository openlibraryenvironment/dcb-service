package org.olf.dcb.request.fulfilment;

import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.interaction.CancelHoldRequestParameters;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.PatronRequest.Status;


import org.olf.dcb.request.workflow.CancelledPatronRequestTransition;
import org.olf.dcb.storage.PatronRequestRepository;

import jakarta.inject.Singleton;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * Patron-initiated cancellation from discovery.
 *
 * This service performs exactly the act the patron could perform in their own
 * OPAC: cancel THEIR hold at THEIR borrowing system. It never touches the
 * supplier side and it does not transition the request itself — the tracking
 * poll observes the cancelled local hold and {@link CancelledPatronRequestTransition}
 * does the state work, exactly as it would for an OPAC-initiated cancellation.
 * The state machine stays the only owner of transitions.
 */
@Slf4j
@Singleton
public class PatronRequestCancellationService {

	private final PatronRequestRepository patronRequestRepository;
	private final HostLmsService hostLmsService;
	private final PatronRequestAuditService patronRequestAuditService;

	/**
	 * The states a patron may cancel from: the request exists at the borrowing system and the item
	 * has not been loaned. Anything later is the state machine's business, not the patron's.
	 * <p>
	 * Deliberately wider than {@code CancelledPatronRequestTransition}'s own source states, which
	 * DCB-2193 narrowed to the two where the item is still at the supplier. In the other three the
	 * item is already out, so the cancellation is parked in AWAITING_RETURN_TO_SUPPLIER by
	 * HandleCancelledRequestItemOut and released once the item is home - it is still a cancellation
	 * the patron is allowed to ask for, which is why this list is its own.
	 */
	private static final List<Status> CANCELLABLE_STATUS = List.of(
		Status.REQUEST_PLACED_AT_BORROWING_AGENCY,
		Status.REQUEST_PLACED_AT_PICKUP_AGENCY,
		Status.PICKUP_TRANSIT,
		Status.RECEIVED_AT_PICKUP,
		Status.READY_FOR_PICKUP
	);

	public PatronRequestCancellationService(PatronRequestRepository patronRequestRepository,
		HostLmsService hostLmsService, PatronRequestAuditService patronRequestAuditService) {

		this.patronRequestRepository = patronRequestRepository;
		this.hostLmsService = hostLmsService;
		this.patronRequestAuditService = patronRequestAuditService;
	}

	/**
	 * Cancels the borrowing-side hold for a request owned by the given patron.
	 *
	 * Empty = no such request for this patron (a 404 upstream). A request in a state
	 * the cancellation transition cannot recover from is rejected with
	 * {@link CancellationNotAllowedException}. A borrowing system that does not
	 * confirm the cancellation raises {@link CancellationFailedException} — never a
	 * success.
	 *
	 * @param assertingService the discovery service that vouched for this patron.
	 *        Recorded in the audit trail: for a self-service mutation, "who asked"
	 *        is the only question the trail ever gets asked.
	 */
	public Mono<CancellationResult> cancelOwnRequest(UUID patronRequestId, String patronSystem,
		String patronId, String assertingService) {

		return Mono.from(patronRequestRepository.findOwnedRequest(patronRequestId, patronSystem, patronId))
			.flatMap(patronRequest -> cancelLocalHold(patronRequest, patronId, assertingService));
	}

	private Mono<CancellationResult> cancelLocalHold(PatronRequest patronRequest, String patronId,
		String assertingService) {

		if (!CANCELLABLE_STATUS.contains(patronRequest.getStatus())
			|| patronRequest.getLocalRequestId() == null
			|| patronRequest.getPatronHostlmsCode() == null) {

			return Mono.error(new CancellationNotAllowedException(patronRequest.getStatus()));
		}

		// Already cancelled at the borrowing system: converge, do not hammer. Every
		// call here is a real LMS write plus a forced poll of borrower, supplier AND
		// pickup — a patron mashing the button must not become an LMS load test.
		if (CancelledPatronRequestTransition.isRequestCancelled(patronRequest.getLocalRequestStatus())) {
			log.info("Request {} local hold is already {}; skipping the LMS call",
				patronRequest.getId(), patronRequest.getLocalRequestStatus());

			return Mono.just(new CancellationResult(patronRequest, true));
		}

		log.info("Patron-initiated cancellation for request {} (status {}) asserted by {}",
			patronRequest.getId(), patronRequest.getStatus(), assertingService);

		return hostLmsService.getClientFor(patronRequest.getPatronHostlmsCode())
			.flatMap(client -> client.cancelHoldRequest(CancelHoldRequestParameters.builder()
				.localRequestId(patronRequest.getLocalRequestId())
				.localItemId(patronRequest.getLocalItemId())
				.patronId(patronId)
				.build()))
			.flatMap(cancelResult -> audit(patronRequest, patronId, assertingService, cancelResult, true)
				.thenReturn(new CancellationResult(patronRequest, false)))
			// An empty result means the client did NOT cancel anything. It must never
			// surface as a 202, and the attempt must be audited either way.
			.switchIfEmpty(Mono.defer(() -> audit(patronRequest, patronId, assertingService, null, false)
				.then(Mono.error(new CancellationFailedException(patronRequest.getPatronHostlmsCode())))));
	}

	private Mono<PatronRequest> audit(PatronRequest patronRequest, String patronId,
		String assertingService, String cancelResult, boolean confirmed) {

		final var auditData = new HashMap<String, Object>();
		auditData.put("localRequestId", patronRequest.getLocalRequestId());
		auditData.put("cancelResult", cancelResult);
		auditData.put("confirmed", confirmed);
		auditData.put("initiatedByPatronId", patronId);
		auditData.put("patronHostLmsCode", patronRequest.getPatronHostlmsCode());
		auditData.put("assertedByDiscoveryService", assertingService);

		return patronRequestAuditService.addAuditEntry(patronRequest, confirmed
				? "Patron cancelled their hold via discovery"
				: "Patron cancellation via discovery NOT CONFIRMED by borrowing system", auditData)
			.thenReturn(patronRequest);
	}

	/**
	 * @param alreadyCancelled true when the local hold was already gone, so the
	 *        caller can skip the forced tracking poll it would otherwise trigger.
	 */
	public record CancellationResult(PatronRequest patronRequest, boolean alreadyCancelled) {
	}

	@Getter
	public static class CancellationNotAllowedException extends RuntimeException {
		private final PatronRequest.Status status;

		public CancellationNotAllowedException(PatronRequest.Status status) {
			super("Request cannot be cancelled from state " + status);
			this.status = status;
		}
	}

	/**
	 * The borrowing system did not confirm the cancellation — most often because its
	 * HostLmsClient does not implement cancelHoldRequest at all. Reporting success
	 * here would tell a patron their request is cancelled while the item still ships.
	 */
	@Getter
	public static class CancellationFailedException extends RuntimeException {
		private final String hostLmsCode;

		public CancellationFailedException(String hostLmsCode) {
			super("Borrowing system " + hostLmsCode + " did not confirm the cancellation");
			this.hostLmsCode = hostLmsCode;
		}
	}
}
