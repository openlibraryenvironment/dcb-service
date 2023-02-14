package org.olf.reshare.dcb.request.fulfilment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.olf.reshare.dcb.core.api.datavalidation.AgencyCommand;
import org.olf.reshare.dcb.core.api.datavalidation.CitationCommand;
import org.olf.reshare.dcb.core.api.datavalidation.PatronRequestCommand;
import org.olf.reshare.dcb.core.api.datavalidation.PickupLocationCommand;
import org.olf.reshare.dcb.core.api.datavalidation.RequestorCommand;
import org.olf.reshare.dcb.storage.PatronRequestRepository;

import reactor.core.publisher.Mono;


class PatronRequestServiceTest {
	@Test
	void shouldReturnErrorWhenDatabaseSavingFails() {
		final var repositoryMock = mock(PatronRequestRepository.class);

		when(repositoryMock.save(any())).thenThrow(new RuntimeException("saving failed"));

		final var service = new PatronRequestService(repositoryMock);

		final var command = Mono.fromSupplier(() -> new PatronRequestCommand(
			UUID.randomUUID(), new CitationCommand(UUID.randomUUID().toString()),
			new RequestorCommand("jane-smith", new AgencyCommand("ABC-123")),
			new PickupLocationCommand("code")));

		final var exception = assertThrows(RuntimeException.class,
			() -> service.savePatronRequest(command).block());

		assertEquals("saving failed", exception.getMessage());
	}
}
