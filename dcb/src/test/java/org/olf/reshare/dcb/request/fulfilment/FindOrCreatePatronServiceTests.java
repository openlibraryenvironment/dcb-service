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

import reactor.core.publisher.Mono;

class FindOrCreatePatronServiceTests {
	@Test
	@DisplayName("should find existing patron when found by patron service")
	void shouldFindExistingPatronWhenPatronIsFound() {
		// Arrange
		final var patronService = mock(PatronService.class);

		final var findOrCreatePatronService = new FindOrCreatePatronService(patronService);

		final var existingPatron = createPatron();

		when(patronService.findPatronFor("localSystemCode", "localId"))
			.thenAnswer(invocation -> Mono.just(existingPatron));

		// Act
		final var foundPatron = findOrCreatePatronService
			.findOrCreatePatron("localSystemCode", "localId").block();

		// Assert
		assertThat("Should be same patron as found by patron service",
			foundPatron, is(existingPatron));

		verify(patronService).findPatronFor("localSystemCode", "localId");
		verifyNoMoreInteractions(patronService);
	}

	@Test
	@DisplayName("should create new patron when no patron is found by patron service")
	void shouldCreateNewPatronWhenNoPatronIsFound() {
		// Arrange
		final var patronService = mock(PatronService.class);

		final var findOrCreatePatronService = new FindOrCreatePatronService(patronService);

		when(patronService.findPatronFor("localSystemCode", "localId"))
			.thenAnswer(invocation -> Mono.empty());

		final var createdPatron = createPatron();

		when(patronService.createPatron("localSystemCode", "localId"))
			.thenAnswer(invocation -> Mono.just(createdPatron));

		// Act
		final var foundPatron = findOrCreatePatronService
			.findOrCreatePatron("localSystemCode", "localId").block();

		// Assert
		assertThat("Should be same patron as created by patron service",
			foundPatron, is(createdPatron));

		verify(patronService).findPatronFor("localSystemCode", "localId");
		verify(patronService).createPatron("localSystemCode", "localId");
		verifyNoMoreInteractions(patronService);
	}

	private static Patron createPatron() {
		return new Patron(randomUUID(), null, null, List.of());
	}
}
