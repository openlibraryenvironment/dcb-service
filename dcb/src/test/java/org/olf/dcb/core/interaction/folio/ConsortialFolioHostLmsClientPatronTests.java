package org.olf.dcb.core.interaction.folio;

import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;
import static org.olf.dcb.test.matchers.interaction.PatronMatchers.hasLocalId;

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
	void shouldBeAbleToFindPatronByBarcode() {
		// Arrange
		final var patron = createPatron("67375297");

		final var localId = UUID.randomUUID().toString();

		mockFolioFixture.mockFindUsersByBarcode("67375297",
			User.builder()
				.id(localId)
				.build());

		// Act
		final var foundPatron = singleValueFrom(client.findVirtualPatron(patron));

		// Assert
		assertThat(foundPatron, allOf(
			hasLocalId(localId)
		));
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
