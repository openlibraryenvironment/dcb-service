package org.olf.dcb.request.fulfilment;

import java.util.HashMap;
import java.util.UUID;

import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.interaction.CancelHoldRequestParameters;
import org.olf.dcb.core.model.PatronRequest;
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

	public PatronRequestCancellationService(PatronRequestRepository patronRequestRepository,
		HostLmsService hostLmsService, PatronRequestAuditService patronRequestAuditService) {

		this.patronRequestRepository = patronRequestRepository;
		this.hostLmsService = hostLmsService;
		this.patronRequestAuditService = patronRequestAuditService;
	}

	/**
	 * Cancels the borrowing-side hold for a request owned by the given patron.
	 * Empty = no such request for this patron (a 404 upstream). A request in a
	 * state the cancellation transition cannot recover from is rejected with
	 * {@link CancellationNotAllowedException}.
	 */
	public Mono<PatronRequest> cancelOwnRequest(UUID patronRequestId, String patronSystem,
		String patronId) {

		return Mono.from(patronRequestRepository.findOwnedRequest(patronRequestId, patronSystem, patronId))
			.flatMap(patronRequest -> cancelLocalHold(patronRequest, patronId));
	}

	private Mono<PatronRequest> cancelLocalHold(PatronRequest patronRequest, String patronId) {
		if (!CancelledPatronRequestTransition.POSSIBLE_SOURCE_STATUS.contains(patronRequest.getStatus())
			|| patronRequest.getLocalRequestId() == null
			|| patronRequest.getPatronHostlmsCode() == null) {

			return Mono.error(new CancellationNotAllowedException(patronRequest.getStatus()));
		}

		log.info("Patron-initiated cancellation for request {} (status {})",
			patronRequest.getId(), patronRequest.getStatus());

		return hostLmsService.getClientFor(patronRequest.getPatronHostlmsCode())
			.flatMap(client -> client.cancelHoldRequest(CancelHoldRequestParameters.builder()
				.localRequestId(patronRequest.getLocalRequestId())
				.localItemId(patronRequest.getLocalItemId())
				.patronId(patronId)
				.build()))
			.flatMap(cancelResult -> {
				final var auditData = new HashMap<String, Object>();
				auditData.put("localRequestId", patronRequest.getLocalRequestId());
				auditData.put("cancelResult", cancelResult);

				return patronRequestAuditService.addAuditEntry(patronRequest,
					"Patron cancelled their hold via discovery", auditData);
			})
			.thenReturn(patronRequest);
	}

	@Getter
	public static class CancellationNotAllowedException extends RuntimeException {
		private final PatronRequest.Status status;

		public CancellationNotAllowedException(PatronRequest.Status status) {
			super("Request cannot be cancelled from state " + status);
			this.status = status;
		}
	}
}
