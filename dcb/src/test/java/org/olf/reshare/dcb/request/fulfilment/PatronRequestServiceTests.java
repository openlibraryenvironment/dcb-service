package org.olf.reshare.dcb.request.fulfilment;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.olf.reshare.dcb.request.fulfilment.PatronRequestStatusConstants.SUBMITTED_TO_DCB;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.olf.reshare.dcb.core.model.Patron;
import org.olf.reshare.dcb.core.model.PatronRequest;
import org.olf.reshare.dcb.storage.PatronRequestRepository;
import reactor.core.publisher.Mono;


class PatronRequestServiceTests {
	@Test
	void shouldReturnErrorWhenDatabaseSavingFails() {
		// Arrange
		final var patronRequestRepository = mock(PatronRequestRepository.class);
		final var requestWorkflow = mock(PatronRequestWorkflow.class);
		final var patronService = mock(PatronService.class);
		final var patronRequestService = new PatronRequestService(
			patronRequestRepository, requestWorkflow, patronService);

		final var command = new PlacePatronRequestCommand(
			new PlacePatronRequestCommand.Citation(UUID.randomUUID()),
			new PlacePatronRequestCommand.PickupLocation("code"),
			new PlacePatronRequestCommand.Requestor(
				new PlacePatronRequestCommand.Agency("ABC-123"),
				"43546", "localSystemCode"));

		when(patronRequestRepository.save(any()))
			.thenThrow(new RuntimeException("saving failed"));

		when(patronService.getOrCreatePatronForRequestor(any()))
			.thenAnswer(invocation -> Mono.just( new Patron() ));

		// Act
		final var exception = assertThrows(RuntimeException.class,
			() -> patronRequestService.placePatronRequest(command).block());

		// Assert
		assertEquals("saving failed", exception.getMessage());
		verify(requestWorkflow, times(0)).initiate(any());
	}


	@Test
	void shouldInitiateRequestWorkflow() {
		// Set up mocks
		PatronRequestRepository patronRequestRepository = mock(PatronRequestRepository.class);
		PatronRequestWorkflow requestWorkflow = mock(PatronRequestWorkflow.class);
		PatronService patronService = mock(PatronService.class);

		PatronRequestService patronRequestService = new PatronRequestService(patronRequestRepository,
			requestWorkflow, patronService);

		// Create necessary test objects
		final var citationId = UUID.randomUUID();
		final var patronId = UUID.randomUUID();
		final var localSystemCode = "localSystemCode";
		final var patronAgencyCode = "patronAgencyCode";
		final var requestId = UUID.randomUUID();
		final var pickupLocationCode = "pickupLocationCode";
		final var patron = new Patron();

		final var command = new PlacePatronRequestCommand(
			new PlacePatronRequestCommand.Citation(citationId),
			new PlacePatronRequestCommand.PickupLocation(pickupLocationCode),
			new PlacePatronRequestCommand.Requestor(
				new PlacePatronRequestCommand.Agency(patronAgencyCode),
				patronId.toString(), localSystemCode));

		final var patronRequest = new PatronRequest(requestId, null, null, patron,
			patronAgencyCode, citationId, pickupLocationCode, SUBMITTED_TO_DCB);

		// Set up mocks
		when(patronService.getOrCreatePatronForRequestor(any()))
			.thenAnswer(invocation -> Mono.just(patron));

		when(patronRequestRepository.save(any()))
			.thenAnswer(invocation -> Mono.just(patronRequest));

		// Call the service method
		patronRequestService.placePatronRequest(command).block();

		// Verify that the workflow was initiated
		verify(requestWorkflow).initiate(any());
	}
}
