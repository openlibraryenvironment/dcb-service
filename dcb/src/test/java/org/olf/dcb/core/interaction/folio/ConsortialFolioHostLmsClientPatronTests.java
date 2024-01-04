package org.olf.dcb.core.interaction.folio;

import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;
import static org.olf.dcb.test.matchers.ThrowableMatchers.hasMessage;
import static org.olf.dcb.test.matchers.interaction.PatronMatchers.hasLocalBarcodes;
import static org.olf.dcb.test.matchers.interaction.PatronMatchers.hasLocalIds;
import static org.olf.dcb.test.matchers.interaction.PatronMatchers.hasLocalNames;
import static org.olf.dcb.test.matchers.interaction.PatronMatchers.hasLocalPatronType;
import static org.olf.dcb.test.matchers.interaction.PatronMatchers.hasNoHomeLibraryCode;
import static org.olf.dcb.test.matchers.interaction.PatronMatchers.hasNoLocalBarcodes;
import static org.olf.dcb.test.matchers.interaction.PatronMatchers.hasNoLocalIds;
import static org.olf.dcb.test.matchers.interaction.PatronMatchers.hasNoLocalNames;
import static org.olf.dcb.test.matchers.interaction.PatronMatchers.hasNoLocalPatronType;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockserver.client.MockServerClient;
import org.olf.dcb.core.interaction.HostLmsClient;
import org.olf.dcb.core.model.Patron;
import org.olf.dcb.core.model.PatronIdentity;
import org.olf.dcb.test.HostLmsFixture;

import jakarta.inject.Inject;
import services.k_int.test.mockserver.MockServerMicronautTest;

@MockServerMicronautTest
class ConsortialFolioHostLmsClientPatronTests {
	private static final String HOST_LMS_CODE = "folio-lms-client-patron-tests";

	@Inject
	private HostLmsFixture hostLmsFixture;

	private MockFolioFixture mockFolioFixture;
	private HostLmsClient client;

	@BeforeEach
	public void beforeEach(MockServerClient mockServerClient) {
		final var API_KEY = "eyJzIjoic2FsdCIsInQiOiJ0ZW5hbnQiLCJ1IjoidXNlciJ9";

		hostLmsFixture.deleteAll();

		hostLmsFixture.createFolioHostLms(HOST_LMS_CODE, "https://fake-folio",
			API_KEY, "", "");

		mockFolioFixture = new MockFolioFixture(mockServerClient, "fake-folio", API_KEY);

		client = hostLmsFixture.createClient(HOST_LMS_CODE);
	}

	@Test
	void shouldBeAbleToFindOnlyUserByBarcode() {
		// Arrange
		final var barcode = "67375297";
		final var localId = UUID.randomUUID().toString();
		final var patronGroup = UUID.randomUUID().toString();

		final var patron = createPatron(barcode);

		mockFolioFixture.mockFindUsersByBarcode(barcode,
			User.builder()
				.id(localId)
				.patronGroup(patronGroup)
				.barcode(barcode)
				.personal(User.PersonalDetails.builder()
					.firstName("first name")
					.middleName("middle name")
					.lastName("last name")
					.preferredFirstName("preferred first name")
					.build())
				.build());

		// Act
		final var foundPatron = singleValueFrom(client.findVirtualPatron(patron));

		// Assert
		assertThat(foundPatron, allOf(
			hasLocalIds(localId),
			hasLocalPatronType(patronGroup),
			hasLocalBarcodes(barcode),
			hasNoHomeLibraryCode(),
			hasLocalNames("first name", "middle name", "last name", "preferred first name")
		));
	}

	@Test
	void shouldBeEmptyWhenNoUserFoundForBarcode() {
		// Arrange
		final var barcode = "47683763";

		final var patron = createPatron(barcode);

		mockFolioFixture.mockFindUsersByBarcode(barcode);

		// Act
		final var foundPatron = singleValueFrom(client.findVirtualPatron(patron));

		// Assert
		assertThat(foundPatron, is(nullValue()));
	}

	@Test
	void shouldTolerateUserWithMissingProperties() {
		// Arrange
		final var barcode = "7848675";

		final var patron = createPatron(barcode);

		mockFolioFixture.mockFindUsersByBarcode(barcode, User.builder().build());

		// Act
		final var foundPatron = singleValueFrom(client.findVirtualPatron(patron));

		// Assert
		assertThat(foundPatron, allOf(
			hasNoLocalIds(),
			hasNoLocalPatronType(),
			hasNoLocalBarcodes(),
			hasNoHomeLibraryCode(),
			hasNoLocalNames()
		));
	}

	@Test
	void shouldFailWhenMultipleUsersFoundForBarcode() {
		// Arrange
		final var barcode = "6349673";

		final var patron = createPatron(barcode);

		mockFolioFixture.mockFindUsersByBarcode(barcode,
			User.builder()
				.id(UUID.randomUUID().toString())
				.build(),
			User.builder()
				.id(UUID.randomUUID().toString())
				.build());

		// Act
		final var exception = assertThrows(MultipleUsersFoundException.class, () ->
			singleValueFrom(client.findVirtualPatron(patron)));

		// Assert
		assertThat(exception, hasMessage(
			"Multiple users found in Host LMS: \"folio-lms-client-patron-tests\" for query: \"barcode==\"6349673\"\""));
	}

	private static Patron createPatron(String localBarcode) {
		return Patron.builder()
			.id(UUID.randomUUID())
			.patronIdentities(List.of(
				PatronIdentity.builder()
					.homeIdentity(true)
					.localBarcode(localBarcode)
					.build()
			))
			.build();
	}
}
