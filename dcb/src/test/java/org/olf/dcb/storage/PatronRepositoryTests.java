package org.olf.dcb.storage;

import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.olf.dcb.core.model.Patron;
import org.olf.dcb.storage.PatronRepository;
import org.olf.dcb.test.DcbTest;
import org.olf.dcb.test.PatronFixture;

import jakarta.inject.Inject;

@DcbTest
class PatronRepositoryTests {
	@Inject
	private PatronRepository patronRepository;

	@Inject
	private PatronFixture patronFixture;

	@BeforeEach
	void beforeEach() {
		patronFixture.deleteAllPatrons();
	}

	@Test
	void shouldSavePatron() {
		// Arrange
		final var patronToSave = Patron.builder()
			.id(randomUUID())
			.patronIdentities(List.of())
			.homeLibraryCode("home-library-code")
			.build();

		// Act
		final var savedPatron = singleValueFrom(patronRepository.save(patronToSave));

		// Assert
		assertThat(savedPatron, is(notNullValue()));

		final var fetchedPatron = singleValueFrom(
			patronRepository.findById(savedPatron.getId()));

		assertThat(fetchedPatron, is(notNullValue()));
		assertThat(fetchedPatron.getHomeLibraryCode(), is("home-library-code"));
		assertThat(fetchedPatron.getDateCreated(), is(notNullValue()));
		assertThat(fetchedPatron.getDateUpdated(), is(notNullValue()));
	}
}
