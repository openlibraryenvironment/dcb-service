package org.olf.reshare.dcb.request.fulfilment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.olf.reshare.dcb.request.resolution.PatronRequestResolutionService;
import org.olf.reshare.dcb.storage.PatronRequestRepository;
import org.olf.reshare.dcb.storage.SupplierRequestRepository;


class PatronRequestServiceTests {
	@Test
	void shouldReturnErrorWhenDatabaseSavingFails() {
		final var patronRequestRepository = mock(PatronRequestRepository.class);
		final var supplierRequestRepository = mock(SupplierRequestRepository.class);
		final var patronRequestResolutionService = mock(PatronRequestResolutionService.class);

		when(patronRequestRepository.save(any()))
			.thenThrow(new RuntimeException("saving failed"));

		final var service = new PatronRequestService(patronRequestRepository, supplierRequestRepository, patronRequestResolutionService);

		final var command = new PlacePatronRequestCommand(
			UUID.randomUUID(), new PlacePatronRequestCommand.Citation(UUID.randomUUID()),
			new PlacePatronRequestCommand.PickupLocation("code"),
			new PlacePatronRequestCommand.Requestor("jane-smith",
				new PlacePatronRequestCommand.Agency("ABC-123")));

		final var exception = assertThrows(RuntimeException.class,
			() -> service.placePatronRequest(command).block());

		assertEquals("saving failed", exception.getMessage());
		verify(supplierRequestRepository, times(0)).save(any());
		verify(patronRequestResolutionService, times(0)).resolvePatronRequest(any());
	}
}
