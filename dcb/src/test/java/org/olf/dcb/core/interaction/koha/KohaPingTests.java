package org.olf.dcb.core.interaction.koha;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockserver.client.MockServerClient;
import org.olf.dcb.core.interaction.HostLmsClient;
import org.olf.dcb.core.interaction.PingResponse;
import org.olf.dcb.core.interaction.koha.dto.KohaLibrary;
import org.olf.dcb.test.HostLmsFixture;

import jakarta.inject.Inject;
import services.k_int.test.mockserver.MockServerMicronautTest;

/**
 * The ping is one authenticated library read: it proves the client credentials mint a
 * token and that the token carries the catalogue permission every other read needs.
 */
@MockServerMicronautTest
class KohaPingTests {
	private static final String KOHA_HOST_LMS_CODE = "koha-ping";
	private static final String KOHA_HOST = "fake-koha-ping";

	@Inject
	private HostLmsFixture hostLmsFixture;

	private MockKohaFixture koha;
	private HostLmsClient client;

	@BeforeEach
	void beforeEach(MockServerClient mockServerClient) {
		mockServerClient.reset();
		hostLmsFixture.deleteAll();

		hostLmsFixture.createKohaHostLms(KOHA_HOST_LMS_CODE, "https://" + KOHA_HOST);
		koha = new MockKohaFixture(mockServerClient, KOHA_HOST);
		client = hostLmsFixture.createClient(KOHA_HOST_LMS_CODE);
	}

	@Test
	void shouldBeOkWhenOneLibraryCanBeRead() {
		// The expectation answers only a read of one library, not a read of the whole list
		koha.mockOneLibrary(KohaLibrary.builder().libraryId("CPL").build());

		final var response = client.ping().block();

		assertThat(response.getStatus(), is(PingResponse.OK));
		assertThat(response.getVersionInfo(), is("KOHA REST API v1"));
	}

	@Test
	void shouldFailWhenKohaRefusesTheClientCredentials() {
		koha.mockLibrariesForbidden();

		final var response = client.ping().block();

		assertThat(response.getStatus(), is(PingResponse.ERROR));
		assertThat(response.getAdditional(), containsString("Koha API Error"));
	}
}
