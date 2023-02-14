package org.olf.reshare.dcb.processing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.olf.reshare.dcb.storage.PatronRequestRepository;

import reactor.core.publisher.Mono;


class PatronRequestServiceTest {
	@Test
	void shouldReturnErrorWhenDatabaseSavingFails() {
		final var repositoryMock = mock(PatronRequestRepository.class);

		when(repositoryMock.save(any()))
			.thenThrow(new RuntimeException("saving failed"));

		final var service = new PatronRequestService(repositoryMock);

		final var command = Mono.fromSupplier(() -> new PatronRequestRecord(
			UUID.randomUUID(), new PatronRequestRecord.Citation(UUID.randomUUID()),
			new PatronRequestRecord.PickupLocation("code"),
			new PatronRequestRecord.Requestor("jane-smith",
				new PatronRequestRecord.Agency("ABC-123"))));

		final var exception = assertThrows(RuntimeException.class,
			() -> service.savePatronRequest(command).block());

		assertEquals("saving failed", exception.getMessage());
	}
}
