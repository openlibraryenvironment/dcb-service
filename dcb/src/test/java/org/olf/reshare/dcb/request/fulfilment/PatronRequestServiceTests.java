package org.olf.reshare.dcb.request.fulfilment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.olf.reshare.dcb.request.fulfilment.PatronRequestStatusConstants.SUBMITTED_TO_DCB;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.olf.reshare.dcb.core.model.PatronRequest;
import org.olf.reshare.dcb.storage.PatronRequestRepository;
import reactor.core.publisher.Mono;


class PatronRequestServiceTests {
	@Test
	void shouldReturnErrorWhenDatabaseSavingFails() {
		final var patronRequestRepository = mock(PatronRequestRepository.class);
		final var requestWorkflow = mock(PatronRequestWorkflow.class);

		final var patronRequestService = new PatronRequestService(patronRequestRepository, requestWorkflow);
		when(patronRequestRepository.save(any()))
			.thenThrow(new RuntimeException("saving failed"));

		final var command = new PlacePatronRequestCommand(
			UUID.randomUUID(), new PlacePatronRequestCommand.Citation(UUID.randomUUID()),
			new PlacePatronRequestCommand.PickupLocation("code"),
			new PlacePatronRequestCommand.Requestor("jane-smith",
				new PlacePatronRequestCommand.Agency("ABC-123")));

		final var exception = assertThrows(RuntimeException.class,
			() -> patronRequestService.placePatronRequest(command).block());

		assertEquals("saving failed", exception.getMessage());
		verify(requestWorkflow, times(0)).initiate(any());
	}

	@Test
	void shouldInitiateRequestWorkflow() {
		final var patronRequestRepository = mock(PatronRequestRepository.class);
		final var requestWorkflow = mock(PatronRequestWorkflow.class);
		final var patronRequestService = new PatronRequestService(patronRequestRepository, requestWorkflow);

		final var patronRequest = new PatronRequest(UUID.randomUUID(), null, null,
			"patronId", "patronAgencyCode",
			UUID.randomUUID(), "pickupLocationCode", SUBMITTED_TO_DCB);

		final var command = new PlacePatronRequestCommand(
			UUID.randomUUID(), new PlacePatronRequestCommand.Citation(UUID.randomUUID()),
			new PlacePatronRequestCommand.PickupLocation("pickupLocationCode"),
			new PlacePatronRequestCommand.Requestor("patronId",
				new PlacePatronRequestCommand.Agency("patronAgencyCode")));

		when(patronRequestRepository.save(any()))
			.thenAnswer(invocation -> Mono.just(patronRequest));

		patronRequestService.placePatronRequest(command).block();
		verify(requestWorkflow).initiate(any());
	}
}
