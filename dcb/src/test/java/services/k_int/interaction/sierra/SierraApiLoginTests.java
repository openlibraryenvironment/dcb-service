package services.k_int.interaction.sierra;

import static io.micronaut.http.HttpStatus.UNAUTHORIZED;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockserver.client.MockServerClient;
import org.mockserver.matchers.Times;
import org.olf.dcb.core.interaction.sierra.SierraItemsAPIFixture;
import org.olf.dcb.core.interaction.sierra.SierraLoginAPIFixture;
import org.olf.dcb.test.HostLmsFixture;

import io.micronaut.context.annotation.Property;
import io.micronaut.core.io.ResourceLoader;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import jakarta.inject.Inject;
import services.k_int.interaction.auth.AuthToken;
import services.k_int.interaction.sierra.items.Params;
import services.k_int.interaction.sierra.patrons.ItemPatch;
import services.k_int.test.mockserver.MockServerMicronautTest;

@MockServerMicronautTest
@Property(name = "r2dbc.datasources.default.options.maxSize", value = "1")
@Property(name = "r2dbc.datasources.default.options.initialSize", value = "1")
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

		hostLmsFixture.deleteAll();

		hostLmsFixture.createSierraHostLms(HOST_LMS_CODE, KEY, SECRET, BASE_URL, "item");
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

	@Test
	void shouldReauthenticateAfterDeniedRequested(MockServerClient mockServerClient) {
		// Arrange
		final var sierraLoginFixture = new SierraLoginAPIFixture(mockServerClient, loader);

		// Login should be re-attempted after unauthorised response
		sierraLoginFixture.successfulLoginFor(KEY, SECRET, "login-token",
			Times.exactly(2));

		final var sierraApiClient = hostLmsFixture.createLowLevelSierraClient(HOST_LMS_CODE, client);

		final var itemsFixture = new SierraItemsAPIFixture(mockServerClient, loader);

		itemsFixture.unauthorisedResponseForCreateItem(12345, "ABC-123",
			"584866478");

		itemsFixture.twoItemsResponseForBibId("12345");

		// Act

		// First request should fail
		final var unauthorisedException = assertThrows(HttpClientResponseException.class,
			() -> singleValueFrom(sierraApiClient.createItem(ItemPatch.builder()
				.bibIds(List.of(12345))
				.barcodes(List.of("584866478"))
				.location("ABC-123")
				.build())));

		// Second request should succeed after login repeated
		final var items = singleValueFrom(sierraApiClient.items(Params.builder()
			.bibId("12345")
			.deleted(false)
			.build()));

		// Assert
		assertThat("Exception should not be null",
			unauthorisedException, is(notNullValue()));

		final var response = unauthorisedException.getResponse();

		assertThat("Should return a unauthorised status",
			response.getStatus(), is(UNAUTHORIZED));

		assertThat("Items should not be null", items, is(notNullValue()));
		assertThat("Should have 2 items", items.getEntries(), hasSize(2));
	}

	private void mockSuccessfulLogin(MockServerClient mock, String token) {
		final var sierraLoginFixture = new SierraLoginAPIFixture(mock, loader);

		sierraLoginFixture.successfulLoginFor(KEY, SECRET, token);
		sierraLoginFixture.failLoginsForAnyOtherCredentials(KEY, SECRET);
	}

	private AuthToken login(String key, String secret) {
		final var sierraApiClient = hostLmsFixture.createLowLevelSierraClient(HOST_LMS_CODE, client);

		return singleValueFrom(sierraApiClient.login(key, secret));
	}
}
