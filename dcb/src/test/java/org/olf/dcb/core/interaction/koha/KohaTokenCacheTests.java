package org.olf.dcb.core.interaction.koha;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockserver.client.MockServerClient;
import org.olf.dcb.core.interaction.PingResponse;
import org.olf.dcb.core.interaction.koha.dto.KohaLibrary;
import org.olf.dcb.test.HostLmsFixture;

import jakarta.inject.Inject;
import reactor.test.StepVerifier;
import services.k_int.test.mockserver.MockServerMicronautTest;

/**
 * Koha keeps every token it issues as a row until its nightly cleanup, so DCB holds one
 * per Host LMS rather than one per client, and fetches another only when Koha refuses it.
 */
@MockServerMicronautTest
class KohaTokenCacheTests {
	private static final String KOHA_HOST_LMS_CODE = "koha-token-cache";
	private static final String KOHA_HOST = "fake-koha-token-cache";
	private static final KohaLibrary LIBRARY = KohaLibrary.builder().libraryId("CPL").build();

	@Inject
	private HostLmsFixture hostLmsFixture;
	@Inject
	private KohaTokenCache tokenCache;

	private MockKohaFixture koha;

	@BeforeEach
	void beforeEach(MockServerClient mockServerClient) {
		mockServerClient.reset();
		hostLmsFixture.deleteAll();
		tokenCache.invalidate(KOHA_HOST_LMS_CODE);

		hostLmsFixture.createKohaHostLms(KOHA_HOST_LMS_CODE, "https://" + KOHA_HOST);
		koha = new MockKohaFixture(mockServerClient, KOHA_HOST);
	}

	@Test
	void shouldShareOneTokenBetweenClientsForOneHostLms() {
		koha.mockOneLibrary(LIBRARY);

		ping();
		ping();

		koha.verifyTokenRequested(1);
	}

	@Test
	void shouldFetchANewTokenOnceWhenKohaRefusesTheCachedOne() {
		koha.mockOneLibraryRefusingTheTokenOnce(LIBRARY);

		assertThat(ping(), is(PingResponse.OK));

		koha.verifyTokenRequested(2);
	}

	@Test
	void shouldNotRetryAWrongPassword() {
		// The validation's 401 is the patron's wrong password: a retry would count as a
		// second failed attempt against Koha's lockout
		koha.mockPasswordRefused("CARD-0001", "wrong", 401);

		StepVerifier.create(hostLmsFixture.createClient(KOHA_HOST_LMS_CODE)
				.patronAuth("BASIC/BARCODE+PASSWORD", "CARD-0001", "wrong"))
			.verifyComplete();

		koha.verifyPasswordValidated("CARD-0001", "wrong", 1);
		koha.verifyTokenRequested(1);
	}

	private String ping() {
		return hostLmsFixture.createClient(KOHA_HOST_LMS_CODE).ping().block().getStatus();
	}
}
