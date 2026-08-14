package org.olf.dcb.request.fulfilment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.olf.dcb.core.interaction.HostLmsRequest.HOLD_CANCELLED;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.interaction.CancelHoldRequestParameters;
import org.olf.dcb.core.interaction.HostLmsClient;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.PatronRequest.Status;
import org.olf.dcb.storage.PatronRequestRepository;

import reactor.core.publisher.Mono;

/**
 * Patron-initiated cancellation must only ever touch the patron's OWN hold at
 * the BORROWING system — never the supplier side — and must refuse any state
 * the cancellation transition cannot recover from (see DCB-2193 for why
 * cancelling the wrong side creates zombie transactions).
 */
class PatronRequestCancellationServiceTests {

	private static final UUID REQUEST_ID = UUID.randomUUID();

	// Created per TEST, not per class. This repo sets
	// junit.jupiter.testinstance.lifecycle.default = per_class in
	// junit-platform.properties, so one instance is shared across every method here —
	// field initialisers run ONCE and Mockito then accumulates invocations across tests.
	// Initialised inline, `verify(..., never())` in one test sees a call another test
	// legitimately made, and fails for a reason that has nothing to do with the code.
	private PatronRequestRepository patronRequestRepository;
	private HostLmsService hostLmsService;
	private PatronRequestAuditService auditService;
	private HostLmsClient borrowingClient;

	private PatronRequestCancellationService service;

	private static final String ASSERTED_BY = "a-discovery-service";

	private static PatronRequest requestInState(Status status) {
		return PatronRequest.builder()
			.id(REQUEST_ID)
			.status(status)
			.patronHostlmsCode("borrowing-lms")
			.localRequestId("hold-77")
			.localItemId("item-9")
			.build();
	}

	private Mono<PatronRequestCancellationService.CancellationResult> cancel(String patronId) {
		return service.cancelOwnRequest(REQUEST_ID, "lms-a", patronId, ASSERTED_BY);
	}

	@BeforeEach
	void stubCollaborators() {
		patronRequestRepository = mock(PatronRequestRepository.class);
		hostLmsService = mock(HostLmsService.class);
		auditService = mock(PatronRequestAuditService.class);
		borrowingClient = mock(HostLmsClient.class);
		service = new PatronRequestCancellationService(
			patronRequestRepository, hostLmsService, auditService);

		when(hostLmsService.getClientFor(eq("borrowing-lms"))).thenReturn(Mono.just(borrowingClient));
		when(borrowingClient.cancelHoldRequest(any())).thenReturn(Mono.just("OK"));
		when(auditService.addAuditEntry(any(PatronRequest.class), anyString(), anyMap()))
			.thenReturn(Mono.empty());
	}

	@Test
	void cancelsTheBorrowingSideHoldForAnOwnedCancellableRequest() {
		when(patronRequestRepository.findOwnedRequest(eq(REQUEST_ID), eq("lms-a"), eq("p-123")))
			.thenReturn(Mono.just(requestInState(Status.REQUEST_PLACED_AT_BORROWING_AGENCY)));

		final var result = cancel("p-123").block();

		assertEquals(REQUEST_ID, result.patronRequest().getId());
		assertFalse(result.alreadyCancelled());

		final var captor = ArgumentCaptor.forClass(CancelHoldRequestParameters.class);
		verify(borrowingClient).cancelHoldRequest(captor.capture());
		// the patron's own hold, identified by the borrowing-side ids and the
		// patron's own local id — nothing supplier-shaped anywhere near this call
		assertEquals("hold-77", captor.getValue().getLocalRequestId());
		assertEquals("item-9", captor.getValue().getLocalItemId());
		assertEquals("p-123", captor.getValue().getPatronId());
	}

	@Test
	void aRequestThatIsNotOwnedStaysEmptyAndTouchesNothing() {
		when(patronRequestRepository.findOwnedRequest(any(), anyString(), anyString()))
			.thenReturn(Mono.empty());

		assertEquals(null, cancel("someone-else").block());

		verify(borrowingClient, never()).cancelHoldRequest(any());
	}

	@Test
	void statesTheCancellationTransitionCannotRecoverFromAreRefused() {
		// LOANED = the item is out; cancelling the hold now would strand the loan
		when(patronRequestRepository.findOwnedRequest(eq(REQUEST_ID), eq("lms-a"), eq("p-123")))
			.thenReturn(Mono.just(requestInState(Status.LOANED)));

		assertThrows(PatronRequestCancellationService.CancellationNotAllowedException.class,
			() -> cancel("p-123").block());

		verify(borrowingClient, never()).cancelHoldRequest(any());
	}

	/**
	 * DCB-2193 narrowed CancelledPatronRequestTransition to the two states where the item is still
	 * at the supplier. A patron may still cancel once the item is out: HandleCancelledRequestItemOut
	 * parks that in AWAITING_RETURN_TO_SUPPLIER until the item is home. This service therefore keeps
	 * its own, wider list, and this is the boundary that must not drift.
	 */
	@Test
	void aRequestWaitingOnTheHoldShelfCanStillBeCancelled() {
		when(patronRequestRepository.findOwnedRequest(eq(REQUEST_ID), eq("lms-a"), eq("p-123")))
			.thenReturn(Mono.just(requestInState(Status.READY_FOR_PICKUP)));

		final var result = cancel("p-123").block();

		assertFalse(result.alreadyCancelled());
		verify(borrowingClient).cancelHoldRequest(any());
	}

	@Test
	void aParkedCancellationIsNotCancelledAgain() {
		// AWAITING_RETURN_TO_SUPPLIER already means "cancelled, waiting for the item to come back"
		when(patronRequestRepository.findOwnedRequest(eq(REQUEST_ID), eq("lms-a"), eq("p-123")))
			.thenReturn(Mono.just(requestInState(Status.AWAITING_RETURN_TO_SUPPLIER)));

		assertThrows(PatronRequestCancellationService.CancellationNotAllowedException.class,
			() -> cancel("p-123").block());

		verify(borrowingClient, never()).cancelHoldRequest(any());
	}

	@Test
	void aRequestWithoutALocalHoldIsRefused() {
		final var noHoldYet = PatronRequest.builder()
			.id(REQUEST_ID)
			.status(Status.REQUEST_PLACED_AT_BORROWING_AGENCY)
			.patronHostlmsCode("borrowing-lms")
			.build();
		when(patronRequestRepository.findOwnedRequest(eq(REQUEST_ID), eq("lms-a"), eq("p-123")))
			.thenReturn(Mono.just(noHoldYet));

		assertThrows(PatronRequestCancellationService.CancellationNotAllowedException.class,
			() -> cancel("p-123").block());
	}

	/**
	 * The bug this guards: AbstractHostLmsClient.cancelHoldRequest returns
	 * Mono.empty(), and FoundationClient and ORSApplianceHostLMS inherit it. The audit
	 * entry used to sit inside a flatMap on the cancel result, so for those systems
	 * NOTHING was cancelled, NOTHING was audited, and the API answered 202 — telling a
	 * patron their request was cancelled while the item still shipped.
	 */
	@Test
	void aBorrowingSystemThatDoesNotConfirmTheCancellationIsAFailureNotASuccess() {
		when(patronRequestRepository.findOwnedRequest(eq(REQUEST_ID), eq("lms-a"), eq("p-123")))
			.thenReturn(Mono.just(requestInState(Status.REQUEST_PLACED_AT_BORROWING_AGENCY)));
		when(borrowingClient.cancelHoldRequest(any())).thenReturn(Mono.empty());

		assertThrows(PatronRequestCancellationService.CancellationFailedException.class,
			() -> cancel("p-123").block());

		// ...and the failed attempt is still on the record.
		final var title = ArgumentCaptor.forClass(String.class);
		final var auditData = ArgumentCaptor.forClass(Map.class);
		verify(auditService).addAuditEntry(any(PatronRequest.class), title.capture(), auditData.capture());

		assertTrue(title.getValue().contains("NOT CONFIRMED"));
		assertEquals(false, auditData.getValue().get("confirmed"));
	}

	@Test
	void aRepeatedCancellationDoesNotHitTheLmsAgain() {
		final var alreadyCancelled = requestInState(Status.REQUEST_PLACED_AT_BORROWING_AGENCY);
		alreadyCancelled.setLocalRequestStatus(HOLD_CANCELLED);

		when(patronRequestRepository.findOwnedRequest(eq(REQUEST_ID), eq("lms-a"), eq("p-123")))
			.thenReturn(Mono.just(alreadyCancelled));

		final var result = cancel("p-123").block();

		// Reported as accepted, so discovery stays idempotent, but flagged so the
		// controller skips the forced tracking poll as well.
		assertTrue(result.alreadyCancelled());
		verify(borrowingClient, never()).cancelHoldRequest(any());
	}

	/** For a self-service mutation, "who asked" is the only question the audit trail gets. */
	@Test
	void theAuditTrailRecordsWhoAssertedThePatron() {
		when(patronRequestRepository.findOwnedRequest(eq(REQUEST_ID), eq("lms-a"), eq("p-123")))
			.thenReturn(Mono.just(requestInState(Status.REQUEST_PLACED_AT_BORROWING_AGENCY)));

		cancel("p-123").block();

		final var auditData = ArgumentCaptor.forClass(Map.class);
		verify(auditService).addAuditEntry(any(PatronRequest.class), anyString(), auditData.capture());

		assertEquals("p-123", auditData.getValue().get("initiatedByPatronId"));
		assertEquals(ASSERTED_BY, auditData.getValue().get("assertedByDiscoveryService"));
		assertEquals(true, auditData.getValue().get("confirmed"));
	}
}
