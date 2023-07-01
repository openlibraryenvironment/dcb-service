package services.k_int.interaction.sierra;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.olf.reshare.dcb.test.PublisherUtils.singleValueFrom;

import java.time.Instant;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockserver.client.MockServerClient;
import org.olf.reshare.dcb.core.interaction.sierra.SierraLoginAPIFixture;
import org.olf.reshare.dcb.test.HostLmsFixture;

import io.micronaut.core.io.ResourceLoader;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import jakarta.inject.Inject;
import services.k_int.interaction.auth.AuthToken;
import services.k_int.test.mockserver.MockServerMicronautTest;

@MockServerMicronautTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SierraApiLoginTests {
	private static final String HOST_LMS_CODE = "sierra-login-api-tests";
	private static final String KEY = "token-key";
	private static final String SECRET = "token-secret";

	@Inject
	private HttpClient client;
	@Inject
	private ResourceLoader loader;
	@Inject
	private HostLmsFixture hostLmsFixture;

	@BeforeAll
	void beforeAll() {
		final String BASE_URL = "https://login-api-tests.com";

		hostLmsFixture.deleteAllHostLMS();

		hostLmsFixture.createSierraHostLms(KEY, SECRET, BASE_URL, HOST_LMS_CODE);
	}

	@Test
	void shouldLoginWhenCredentialsAreValid(MockServerClient mockServer) {
		mockSuccessfulLogin(mockServer, "login-token");

		var token = login(KEY, SECRET);

		assertThat("Token should not be null", token, is(notNullValue()));
		assertThat("Should be bearer token", token.type().toLowerCase(), is("bearer"));

		assertThat("Token should not be expired", token.isExpired(), is(false));
		assertThat("Token should expire in the future",
			token.expires().isAfter(Instant.now()), is(true));
	}

	@Test
	void shouldFailToLoginWhenCredentialsAreInvalid(MockServerClient mock) {
		mockSuccessfulLogin(mock, "login-token");

		final var exception = assertThrows(HttpClientResponseException.class,
			() -> login("WRONG_KEY", "WRONG_SECRET"));

		assertThat("Exception should not be null", exception, is(notNullValue()));
		assertThat("Status should not be null", exception.getStatus(), is(notNullValue()));

		assertThat("Code should be unauthorised",
			exception.getStatus().getCode(), is(401));
	}

	@Test
	void eachLoginShouldProduceADifferentToken(MockServerClient mock) {
		mockSuccessfulLogin(mock, "first-login-token");
		mockSuccessfulLogin(mock, "second-login-token");

		var token1 = login(KEY, SECRET);
		var token2 = login(KEY, SECRET);

		assertThat("Token 1 should not be null", token1, is(notNullValue()));
		assertThat("Token 2 should not be null", token2, is(notNullValue()));

		assertThat("Should be first token", token1.value(), is("first-login-token"));
		assertThat("Should be second token", token2.value(), is("second-login-token"));
	}

	private void mockSuccessfulLogin(MockServerClient mock, String token) {
		final var sierraLoginFixture = new SierraLoginAPIFixture(mock, loader);

		sierraLoginFixture.successfulLoginFor(KEY, SECRET, token);
		sierraLoginFixture.failLoginsForAnyOtherCredentials(KEY, SECRET);
	}

	private AuthToken login(String key, String secret) {
		final var sierraApiClient = hostLmsFixture.createClient(HOST_LMS_CODE, client);

		return singleValueFrom(sierraApiClient.login(key, secret));
	}
}
