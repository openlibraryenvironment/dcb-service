package org.olf.dcb.core.interaction.folio;

import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;
import static org.olf.dcb.test.matchers.interaction.PatronMatchers.hasLocalBarcodes;
import static org.olf.dcb.test.matchers.interaction.PatronMatchers.hasLocalIds;
import static org.olf.dcb.test.matchers.interaction.PatronMatchers.hasLocalPatronType;
import static org.olf.dcb.test.matchers.interaction.PatronMatchers.hasNoHomeLibraryCode;
import static org.olf.dcb.test.matchers.interaction.PatronMatchers.hasNoLocalNames;

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
				.build());

		// Act
		final var foundPatron = singleValueFrom(client.findVirtualPatron(patron));

		// Assert
		assertThat(foundPatron, allOf(
			hasLocalIds(localId),
			hasLocalPatronType(patronGroup),
			hasLocalBarcodes(barcode),
			hasNoHomeLibraryCode(),
			hasNoLocalNames()
		));
	}

	@Test
	void shouldBeEmptyWhenNoUserFoundForBarcode() {
		// Arrange
		final var patron = createPatron("47683763");

		mockFolioFixture.mockFindUsersByBarcode("47683763");

		// Act
		final var foundPatron = singleValueFrom(client.findVirtualPatron(patron));

		// Assert
		assertThat(foundPatron, is(nullValue()));
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
