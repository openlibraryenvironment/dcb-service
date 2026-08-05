package org.olf.dcb.request.fulfilment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

	private final PatronRequestRepository patronRequestRepository = mock(PatronRequestRepository.class);
	private final HostLmsService hostLmsService = mock(HostLmsService.class);
	private final PatronRequestAuditService auditService = mock(PatronRequestAuditService.class);
	private final HostLmsClient borrowingClient = mock(HostLmsClient.class);

	private final PatronRequestCancellationService service =
		new PatronRequestCancellationService(patronRequestRepository, hostLmsService, auditService);

	private static PatronRequest requestInState(Status status) {
		return PatronRequest.builder()
			.id(REQUEST_ID)
			.status(status)
			.patronHostlmsCode("borrowing-lms")
			.localRequestId("hold-77")
			.localItemId("item-9")
			.build();
	}

	@BeforeEach
	void stubCollaborators() {
		when(hostLmsService.getClientFor(eq("borrowing-lms"))).thenReturn(Mono.just(borrowingClient));
		when(borrowingClient.cancelHoldRequest(any())).thenReturn(Mono.just("OK"));
		when(auditService.addAuditEntry(any(PatronRequest.class), anyString(), anyMap()))
			.thenReturn(Mono.empty());
	}

	@Test
	void cancelsTheBorrowingSideHoldForAnOwnedCancellableRequest() {
		when(patronRequestRepository.findOwnedRequest(eq(REQUEST_ID), eq("lms-a"), eq("p-123")))
			.thenReturn(Mono.just(requestInState(Status.REQUEST_PLACED_AT_BORROWING_AGENCY)));

		final var cancelled = service.cancelOwnRequest(REQUEST_ID, "lms-a", "p-123").block();

		assertEquals(REQUEST_ID, cancelled.getId());

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

		assertEquals(null, service.cancelOwnRequest(REQUEST_ID, "lms-a", "someone-else").block());

		verify(borrowingClient, never()).cancelHoldRequest(any());
	}

	@Test
	void statesTheCancellationTransitionCannotRecoverFromAreRefused() {
		// LOANED = the item is out; cancelling the hold now would strand the loan
		when(patronRequestRepository.findOwnedRequest(eq(REQUEST_ID), eq("lms-a"), eq("p-123")))
			.thenReturn(Mono.just(requestInState(Status.LOANED)));

		assertThrows(PatronRequestCancellationService.CancellationNotAllowedException.class,
			() -> service.cancelOwnRequest(REQUEST_ID, "lms-a", "p-123").block());

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
			() -> service.cancelOwnRequest(REQUEST_ID, "lms-a", "p-123").block());
	}
}
