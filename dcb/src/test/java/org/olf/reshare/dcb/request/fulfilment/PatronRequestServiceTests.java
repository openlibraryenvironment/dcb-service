package org.olf.reshare.dcb.request.fulfilment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.olf.reshare.dcb.request.fulfilment.PatronRequestStatusConstants.SUBMITTED_TO_DCB;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.olf.reshare.dcb.core.model.Patron;
import org.olf.reshare.dcb.core.model.PatronRequest;
import org.olf.reshare.dcb.request.fulfilment.PlacePatronRequestCommand.Citation;
import org.olf.reshare.dcb.request.fulfilment.PlacePatronRequestCommand.PickupLocation;
import org.olf.reshare.dcb.request.fulfilment.PlacePatronRequestCommand.Requestor;
import org.olf.reshare.dcb.storage.PatronRequestRepository;

import reactor.core.publisher.Mono;


class PatronRequestServiceTests {
	@Test
	void shouldReturnErrorWhenDatabaseSavingFails() {
		// Arrange
		final var patronRequestRepository = mock(PatronRequestRepository.class);
		final var requestWorkflow = mock(PatronRequestWorkflow.class);
		final var patronService = mock(PatronService.class);
		final var findOrCreatePatronService = mock(FindOrCreatePatronService.class);

		final var patronRequestService = new PatronRequestService(
			patronRequestRepository, requestWorkflow, patronService, findOrCreatePatronService);

		final var command = new PlacePatronRequestCommand(
			new Citation(UUID.randomUUID()), new PickupLocation("code"),
			new Requestor("43546", "localSystemCode",
				"home-library-code"));

		when(findOrCreatePatronService.findOrCreatePatron(any(), any(), any()))
			.thenAnswer(invocation -> Mono.just(new Patron()));

		when(patronRequestRepository.save(any()))
			.thenThrow(new RuntimeException("saving failed"));

		// Act
		final var exception = assertThrows(RuntimeException.class,
			() -> patronRequestService.placePatronRequest(command).block());

		// Assert
		assertEquals("saving failed", exception.getMessage());

		verify(requestWorkflow, never()).initiate(any());
	}

	@Test
	void shouldInitiateRequestWorkflow() {
		// Arrange
		final var patronRequestRepository = mock(PatronRequestRepository.class);
		final var requestWorkflow = mock(PatronRequestWorkflow.class);
		final var patronService = mock(PatronService.class);
		final var findOrCreatePatronService = mock(FindOrCreatePatronService.class);

		PatronRequestService patronRequestService = new PatronRequestService(
			patronRequestRepository, requestWorkflow, patronService, findOrCreatePatronService);

		final var bibClusterId = UUID.randomUUID();
		final var patronId = UUID.randomUUID();
		final var requestId = UUID.randomUUID();
		final var patron = new Patron();

		final var pickupLocationCode = "pickupLocationCode";

		final var command = new PlacePatronRequestCommand(
			new Citation(bibClusterId),
			new PickupLocation(pickupLocationCode),
			new Requestor(patronId.toString(), "localSystemCode",
				"home-library-code"));

		final var patronRequest = PatronRequest.builder()
			.id(requestId)
			.patron(patron)
			.bibClusterId(bibClusterId)
			.pickupLocationCode(pickupLocationCode)
			.statusCode(SUBMITTED_TO_DCB)
			.build();

		when(findOrCreatePatronService.findOrCreatePatron(any(), any(), any()))
			.thenAnswer(invocation -> Mono.just(patron));

		when(patronRequestRepository.save(any()))
			.thenAnswer(invocation -> Mono.just(patronRequest));

		// Act
		patronRequestService.placePatronRequest(command).block();

		// Assert
		verify(requestWorkflow).initiate(any());
	}
}
