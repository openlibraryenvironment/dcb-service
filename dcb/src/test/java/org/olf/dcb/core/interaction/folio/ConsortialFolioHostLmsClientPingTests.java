package org.olf.dcb.core.interaction.folio;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockserver.model.HttpResponse.response;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockserver.client.MockServerClient;
import org.olf.dcb.core.interaction.PingResponse;
import org.olf.dcb.test.HostLmsFixture;

import jakarta.inject.Inject;
import services.k_int.test.mockserver.MockServerMicronautTest;

/** edge-dcb answers a status lookup for an unknown transaction 404 with a valid key, 401 without. */
@MockServerMicronautTest
class ConsortialFolioHostLmsClientPingTests {
	private static final String HOST_LMS_CODE = "folio-ping-tests";
	private static final String API_KEY = "eyJzIjoic2FsdCIsInQiOiJ0ZW5hbnQiLCJ1IjoidXNlciJ9";

	@Inject
	private HostLmsFixture hostLmsFixture;

	private MockFolioFixture mockFolioFixture;

	@BeforeEach
	void beforeEach(MockServerClient mockServerClient) {
		mockServerClient.reset();
		hostLmsFixture.deleteAll();

		hostLmsFixture.createFolioHostLms(HOST_LMS_CODE, "https://fake-folio-ping", API_KEY, "", "");

		mockFolioFixture = new MockFolioFixture(mockServerClient, "fake-folio-ping", API_KEY);
	}

	@Test
	void shouldBeOkWhenEdgeDcbAcceptsTheKeyAndDoesNotKnowTheTransaction() {
		mockFolioFixture.mockGetAnyTransactionStatus(response().withStatusCode(404));

		final var response = ping();

		assertThat(response.getStatus(), is(PingResponse.OK));
		assertThat(response.getVersionInfo(), is("FOLIO edge-dcb"));
	}

	@Test
	void shouldSayTheKeyWasRefusedWhenEdgeDcbAnswersUnauthorised() {
		mockFolioFixture.mockGetAnyTransactionStatus(response().withStatusCode(401));

		final var response = ping();

		assertThat(response.getStatus(), is(PingResponse.ERROR));
		assertThat(response.getAdditional(), is("edge-dcb refused the API key"));
	}

	private PingResponse ping() {
		return singleValueFrom(hostLmsFixture.createClient(HOST_LMS_CODE).ping());
	}
}
