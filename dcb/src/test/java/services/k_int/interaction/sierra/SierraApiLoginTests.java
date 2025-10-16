package services.k_int.interaction.sierra;

import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;
import static org.olf.dcb.test.matchers.interaction.HttpResponseProblemMatchers.hasMessageForRequest;
import static org.olf.dcb.test.matchers.interaction.HttpResponseProblemMatchers.hasNoResponseBody;
import static org.olf.dcb.test.matchers.interaction.HttpResponseProblemMatchers.hasRequestMethod;
import static org.olf.dcb.test.matchers.interaction.HttpResponseProblemMatchers.hasResponseStatusCode;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockserver.client.MockServerClient;
import org.mockserver.matchers.Times;
import org.olf.dcb.core.interaction.sierra.SierraApiFixtureProvider;
import org.olf.dcb.core.interaction.sierra.SierraItem;
import org.olf.dcb.test.HostLmsFixture;
import org.zalando.problem.ThrowableProblem;

import io.micronaut.http.client.HttpClient;
import jakarta.inject.Inject;
import services.k_int.interaction.auth.AuthToken;
import services.k_int.interaction.sierra.items.Params;
import services.k_int.interaction.sierra.patrons.ItemPatch;
import services.k_int.test.mockserver.MockServerMicronautTest;

@MockServerMicronautTest
@TestInstance(PER_CLASS)
class SierraApiLoginTests {
	private static final String HOST_LMS_CODE = "sierra-login-api-tests";
	private static final String KEY = "token-key";
	private static final String SECRET = "token-secret";

	@Inject
	private HttpClient client;
	@Inject
	private HostLmsFixture hostLmsFixture;
	@Inject
	private SierraApiFixtureProvider sierraApiFixtureProvider;

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
		// Arrange
		mockSuccessfulLogin(mock, "login-token");

		// Act
		final var problem = assertThrows(ThrowableProblem.class,
			() -> login("WRONG_KEY", "WRONG_SECRET"));

		// Assert
		assertThat(problem, allOf(
			hasMessageForRequest("POST", "/iii/sierra-api/v6/token"),
			hasResponseStatusCode(401),
			hasNoResponseBody(),
			hasRequestMethod("POST")
		));
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
		final var sierraLoginFixture = sierraApiFixtureProvider.loginFixtureFor(mockServerClient);

		// Login should be re-attempted after unauthorised response
		sierraLoginFixture.successfulLoginFor(KEY, SECRET, "login-token",
			Times.exactly(2));

		final var itemsFixture = sierraApiFixtureProvider.itemsApiFor(mockServerClient);

		itemsFixture.unauthorisedResponseForCreateItem(12345, "ABC-123", "584866478");

		itemsFixture.itemsForBibId("12345", List.of(
			SierraItem.builder()
				.id("864477")
				.locationCode("example-location")
				.locationName("Example Location")
				.build(),
			SierraItem.builder()
				.id("3669266")
				.locationCode("example-location")
				.locationName("Example Location")
				.build()
			));

		// Act
		final var sierraApiClient = hostLmsFixture.createLowLevelSierraClient(HOST_LMS_CODE);

		// First request should fail
		final var problem = assertThrows(ThrowableProblem.class,
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
		assertThat(problem, allOf(
			hasMessageForRequest("POST", "/iii/sierra-api/v6/items"),
			hasResponseStatusCode(401),
			hasNoResponseBody(),
			hasRequestMethod("POST")
		));

		assertThat("Items should not be null", items, is(notNullValue()));
		assertThat("Should have 2 items", items.getEntries(), hasSize(2));
	}

	private void mockSuccessfulLogin(MockServerClient mockServerClient, String token) {
		final var sierraLoginFixture = sierraApiFixtureProvider.loginFixtureFor(mockServerClient);

		sierraLoginFixture.successfulLoginFor(KEY, SECRET, token);
		sierraLoginFixture.failLoginsForAnyOtherCredentials(KEY, SECRET);
	}

	private AuthToken login(String key, String secret) {
		final var sierraApiClient = hostLmsFixture.createLowLevelSierraClient(HOST_LMS_CODE);

		return singleValueFrom(sierraApiClient.login(key, secret));
	}
}
