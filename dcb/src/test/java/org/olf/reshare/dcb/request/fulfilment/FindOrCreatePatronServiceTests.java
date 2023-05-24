package org.olf.reshare.dcb.request.fulfilment;

import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.olf.reshare.dcb.core.model.Patron;
import org.olf.reshare.dcb.request.fulfilment.PatronService.PatronId;

import reactor.core.publisher.Mono;

class FindOrCreatePatronServiceTests {
	private final PatronService patronService = mock(PatronService.class);

	private final FindOrCreatePatronService findOrCreatePatronService = new FindOrCreatePatronService(patronService);

	@Test
	@DisplayName("should find existing patron when found by patron service")
	void shouldFindExistingPatronWhenPatronIsFound() {
		// Arrange
		final var existingPatron = createPatron(null);

		final var patronId = PatronId.fromPatron(existingPatron);

		when(patronService.findPatronFor("localSystemCode", "localId"))
			.thenReturn(Mono.just(patronId));

		when(patronService.findById(patronId))
			.thenReturn(Mono.just(existingPatron));

		// Act

		// Pass a different home library code to demonstrate that it isn't used
		final var foundPatron = findOrCreatePatronService
			.findOrCreatePatron("localSystemCode", "localId", "other-library-code")
			.block();

		// Assert
		assertThat("Should be same patron as found by patron service",
			foundPatron, is(existingPatron));

		verify(patronService).findPatronFor("localSystemCode", "localId");

		verify(patronService).findById(patronId);

		verifyNoMoreInteractions(patronService);
	}

	@Test
	@DisplayName("should create new patron when no patron is found by patron service")
	void shouldCreateNewPatronWhenNoPatronIsFound() {
		// Arrange
		when(patronService.findPatronFor("localSystemCode", "localId"))
			.thenReturn(Mono.empty());

		final var createdPatron = createPatron("home-library-code");

		final var patronId = PatronId.fromPatron(createdPatron);

		when(patronService
			.createPatron("localSystemCode", "localId", "home-library-code"))
				.thenReturn(Mono.just(patronId));

		when(patronService.findById(patronId))
			.thenReturn(Mono.just(createdPatron));

		// Act
		final var foundPatron = findOrCreatePatronService
			.findOrCreatePatron("localSystemCode", "localId", "home-library-code")
			.block();

		// Assert
		assertThat("Should return a patron", foundPatron, is(createdPatron));

		verify(patronService).findPatronFor("localSystemCode", "localId");
		verify(patronService).createPatron("localSystemCode", "localId",
			"home-library-code");

		verify(patronService).findById(patronId);

		verifyNoMoreInteractions(patronService);
	}

	private static Patron createPatron(String homeLibraryCode) {
		return new Patron(randomUUID(), null, null,
			homeLibraryCode, List.of());
	}
}
