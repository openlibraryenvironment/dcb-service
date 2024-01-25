package org.olf.dcb.core.interaction.folio;

import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;
import static org.olf.dcb.test.matchers.HostLmsRequestMatchers.hasLocalId;
import static org.olf.dcb.test.matchers.HostLmsRequestMatchers.hasStatus;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockserver.client.MockServerClient;
import org.olf.dcb.test.HostLmsFixture;

import jakarta.inject.Inject;
import services.k_int.test.mockserver.MockServerMicronautTest;

@MockServerMicronautTest
class ConsortialFolioHostLmsClientGetRequestTests {
	private static final String HOST_LMS_CODE = "folio-get-request-tests";

	@Inject
	private HostLmsFixture hostLmsFixture;

	private MockFolioFixture mockFolioFixture;

	@BeforeEach
	void beforeEach(MockServerClient mockServerClient) {
		final var API_KEY = "eyJzIjoic2FsdCIsInQiOiJ0ZW5hbnQiLCJ1IjoidXNlciJ9";

		hostLmsFixture.deleteAll();

		hostLmsFixture.createFolioHostLms(HOST_LMS_CODE, "https://fake-folio",
			API_KEY, "", "");

		mockFolioFixture = new MockFolioFixture(mockServerClient, "fake-folio", API_KEY);
	}

	@Test
	void shouldDetectRequestHasBeenPlaced() {
		// Arrange
		final var localRequestId = UUID.randomUUID().toString();

		mockFolioFixture.mockGetTransactionStatus(localRequestId, "CREATED");

		// Act
		final var client = hostLmsFixture.createClient(HOST_LMS_CODE);

		final var localRequest = singleValueFrom(client.getRequest(localRequestId));

		// Assert
		assertThat(localRequest, allOf(
			notNullValue(),
			hasLocalId(localRequestId),
			hasStatus("PLACED")
		));
	}
}
