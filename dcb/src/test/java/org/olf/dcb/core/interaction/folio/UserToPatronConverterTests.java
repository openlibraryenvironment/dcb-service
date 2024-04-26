package org.olf.dcb.core.interaction.folio;

import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.olf.dcb.test.matchers.interaction.PatronMatchers.hasLocalBarcodes;
import static org.olf.dcb.test.matchers.interaction.PatronMatchers.hasLocalIds;
import static org.olf.dcb.test.matchers.interaction.PatronMatchers.hasLocalNames;
import static org.olf.dcb.test.matchers.interaction.PatronMatchers.hasLocalPatronType;
import static org.olf.dcb.test.matchers.interaction.PatronMatchers.hasNoCanonicalPatronType;
import static org.olf.dcb.test.matchers.interaction.PatronMatchers.hasNoHomeLibraryCode;
import static org.olf.dcb.test.matchers.interaction.PatronMatchers.isBlocked;
import static org.olf.dcb.test.matchers.interaction.PatronMatchers.isNotBlocked;

import org.junit.jupiter.api.Test;
import org.olf.dcb.core.interaction.Patron;

import io.micronaut.core.convert.ConversionService;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;

@MicronautTest
class UserToPatronConverterTests {
	@Inject
	private ConversionService conversionService;

	@Test
	void shouldMapUserToPatron() {
		// Arrange
		final var barcode = "67375297";
		final var localId = randomUUID().toString();
		final var patronGroupName = "undergraduate";

		final var user = User.builder()
			.id(localId)
			.patronGroupName(patronGroupName)
			.barcode(barcode)
			.personal(User.PersonalDetails.builder()
				.firstName("first name")
				.middleName("middle name")
				.lastName("last name")
				.preferredFirstName("preferred first name")
				.build())
			.blocked(false)
			.build();

		// Act
		final var patron = conversionService.convert(user, Patron.class).orElseThrow();

		// Assert
		assertThat(patron, allOf(
			notNullValue(),
			hasLocalIds(localId),
			hasLocalPatronType(patronGroupName),
			hasNoCanonicalPatronType(),
			hasLocalBarcodes(barcode),
			hasNoHomeLibraryCode(),
			hasLocalNames("first name", "middle name", "last name"),
			isNotBlocked()
		));
	}

	@Test
	void shouldMapBlockedUserToPatron() {
		// Arrange
		final var barcode = "67375297";
		final var localId = randomUUID().toString();
		final var patronGroupName = "undergraduate";

		final var user = User.builder()
			.id(localId)
			.patronGroupName(patronGroupName)
			.barcode(barcode)
			.personal(User.PersonalDetails.builder()
				.firstName("first name")
				.middleName("middle name")
				.lastName("last name")
				.preferredFirstName("preferred first name")
				.build())
			.blocked(true)
			.build();

		// Act
		final var patron = conversionService.convert(user, Patron.class).orElseThrow();

		// Assert
		assertThat(patron, allOf(
			notNullValue(),
			isBlocked()
		));
	}

	@Test
	void shouldDefaultToNotBlockedWhenNoBlockedFieldProvided() {
		// Arrange
		final var barcode = "67375297";
		final var localId = randomUUID().toString();
		final var patronGroupName = "undergraduate";

		final var user = User.builder()
			.id(localId)
			.patronGroupName(patronGroupName)
			.barcode(barcode)
			.personal(User.PersonalDetails.builder()
				.firstName("first name")
				.middleName("middle name")
				.lastName("last name")
				.preferredFirstName("preferred first name")
				.build())
			.build();

		// Act
		final var patron = conversionService.convert(user, Patron.class).orElseThrow();

		// Assert
		assertThat(patron, allOf(
			notNullValue(),
			isNotBlocked()
		));
	}
}
