package org.olf.dcb.core.interaction.polaris;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.mockserver.verify.VerificationTimes.exactly;
import static org.mockserver.verify.VerificationTimes.once;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;

import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockserver.client.MockServerClient;
import org.olf.dcb.test.HostLmsFixture;
import org.olf.dcb.test.TestResourceLoaderProvider;

import jakarta.inject.Inject;
import services.k_int.test.mockserver.MockServerMicronautTest;

/**
 * Proves the token cache is actually wired into the Application Services auth filter, by counting
 * the authentication requests Polaris receives.
 *
 * Each test uses its own Host LMS code because the cache is a singleton keyed by that code - a
 * shared code would let one test inherit another's cached token.
 */
@MockServerMicronautTest
@TestInstance(PER_CLASS)
class PolarisAuthTokenCachingTests {
	private static final String HOST = "polaris-token-caching-tests.com";
	private static final String BASE_URL = "https://" + HOST;

	@Inject
	private TestResourceLoaderProvider testResourceLoaderProvider;

	@Inject
	private HostLmsFixture hostLmsFixture;

	private MockPolarisFixture mockPolarisFixture;
	private MockServerClient mockServerClient;

	@BeforeAll
	void beforeAll(MockServerClient mockServerClient) {
		this.mockServerClient = mockServerClient;

		mockPolarisFixture = new MockPolarisFixture(HOST, mockServerClient,
			testResourceLoaderProvider);
	}

	@BeforeEach
	void beforeEach() {
		// Requests accumulate for the lifetime of the mock server, so counting them only means
		// anything if each test starts from a clean slate.
		mockServerClient.reset();
		hostLmsFixture.deleteAll();

		mockPolarisFixture.mockAppServicesStaffAuthentication();
		mockPolarisFixture.mockGetHoldRequestDefaults(5);
	}

	@Test
	void shouldOnlyAuthenticateOnceAcrossSeparateRequests() {
		final var hostLmsCode = "polaris-token-cache-enabled";

		createPolarisHostLms(hostLmsCode, "900");

		// A fresh client each time, exactly as HostLmsService hands one out per operation. This is
		// the case that used to cost two round trips to Polaris for every single call.
		callPolaris(hostLmsCode);
		callPolaris(hostLmsCode);

		mockPolarisFixture.verifyAppServicesStaffAuthentication(once());
	}

	@Test
	void shouldAuthenticateForEveryRequestWhenCachingIsDisabled() {
		final var hostLmsCode = "polaris-token-cache-disabled";

		createPolarisHostLms(hostLmsCode, "0");

		callPolaris(hostLmsCode);
		callPolaris(hostLmsCode);

		// The contrast that stops the test above passing for the wrong reason: with caching off
		// the handshake really does happen once per request.
		mockPolarisFixture.verifyAppServicesStaffAuthentication(exactly(2));
	}

	@Test
	void shouldAuthenticateAgainWhenPolarisRejectsACachedToken() {
		final var hostLmsCode = "polaris-token-cache-invalidation";

		createPolarisHostLms(hostLmsCode, "900");

		mockServerClient.reset();
		mockPolarisFixture.mockAppServicesStaffAuthentication();
		// Registered first so it wins for the first request only
		mockPolarisFixture.mockGetHoldRequestDefaultsUnauthorisedOnce();
		mockPolarisFixture.mockGetHoldRequestDefaults(5);

		final var rejected = singleValueFrom(hostLmsFixture.createClient(hostLmsCode).ping());
		assertThat("Polaris rejected the token", rejected.getStatus(), is("ERROR"));

		final var recovered = singleValueFrom(hostLmsFixture.createClient(hostLmsCode).ping());
		assertThat("Should recover with a freshly acquired token",
			recovered.getStatus(), is("OK"));

		// Caching is enabled, so without the invalidate-on-401 hook the second call would have
		// reused the rejected token and this would be once() - a stuck host for the whole TTL.
		mockPolarisFixture.verifyAppServicesStaffAuthentication(exactly(2));
	}

	private void createPolarisHostLms(String hostLmsCode, String tokenCacheTtlSeconds) {
		final var key = "polaris-token-caching-key";
		final var secret = "polaris-token-caching-secret";

		hostLmsFixture.createPolarisHostLms(hostLmsCode, key, secret, BASE_URL, "TEST",
			key, secret, null, 73, Map.of("token-cache-ttl-seconds", tokenCacheTtlSeconds));
	}

	/**
	 * ping() is a single Application Services GET behind the staff auth filter, which makes it the
	 * least entangled call available for counting authentication requests.
	 */
	private void callPolaris(String hostLmsCode) {
		final var response = singleValueFrom(hostLmsFixture.createClient(hostLmsCode).ping());

		// Asserted so that a broken request path cannot masquerade as a cache hit.
		assertThat("Polaris call should have succeeded", response.getStatus(), is("OK"));
	}
}
