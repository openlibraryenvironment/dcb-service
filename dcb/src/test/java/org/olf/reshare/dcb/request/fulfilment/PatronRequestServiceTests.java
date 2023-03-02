package org.olf.reshare.dcb.request.fulfilment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.olf.reshare.dcb.request.resolution.SupplierRequestService;
import org.olf.reshare.dcb.storage.PatronRequestRepository;

import reactor.core.publisher.Mono;


class PatronRequestServiceTests {
	@Test
	void shouldReturnErrorWhenDatabaseSavingFails() {
		final var repositoryMock = mock(PatronRequestRepository.class);
		final var supplierRequestServiceMock = mock(SupplierRequestService.class);

		when(repositoryMock.save(any()))
			.thenThrow(new RuntimeException("saving failed"));

		final var service = new PatronRequestService(repositoryMock, supplierRequestServiceMock);

		final var command = Mono.fromSupplier(() -> new PlacePatronRequestCommand(
			UUID.randomUUID(), new PlacePatronRequestCommand.Citation(UUID.randomUUID()),
			new PlacePatronRequestCommand.PickupLocation("code"),
			new PlacePatronRequestCommand.Requestor("jane-smith",
				new PlacePatronRequestCommand.Agency("ABC-123"))));

		final var exception = assertThrows(RuntimeException.class,
			() -> service.placePatronRequest(command).block());

		assertEquals("saving failed", exception.getMessage());
	}
}
