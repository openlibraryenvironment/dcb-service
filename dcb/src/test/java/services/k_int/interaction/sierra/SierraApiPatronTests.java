package services.k_int.interaction.sierra;

import static io.micronaut.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.micronaut.http.client.exceptions.HttpClientResponseException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockserver.client.MockServerClient;
import org.olf.reshare.dcb.core.HostLmsService;
import org.olf.reshare.dcb.core.interaction.sierra.HostLmsSierraApiClient;

import io.micronaut.context.annotation.Property;
import io.micronaut.core.io.ResourceLoader;
import io.micronaut.http.client.HttpClient;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.olf.reshare.dcb.core.interaction.sierra.SierraPatronsAPIFixture;
import reactor.core.publisher.Mono;
import services.k_int.interaction.sierra.patrons.PatronPatch;
import services.k_int.interaction.sierra.patrons.Result;
import services.k_int.test.mockserver.MockServerMicronautTest;

@MockServerMicronautTest
@MicronautTest(transactional = false, propertySources = { "classpath:configs/SierraApiItemTests.yml" }, rebuildContext = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class SierraApiPatronTests {
	private static final String SIERRA_TOKEN = "test-token-for-user";

	// Properties should line up with included property source for the spec.
	@Property(name = "hosts.sierra-items-api-tests.client.base-url")
	private String sierraHost;

	@Property(name = "hosts.sierra-items-api-tests.client.key")
	private String sierraUser;

	@Property(name = "hosts.sierra-items-api-tests.client.secret")
	private String sierraPass;
	@Inject
	private HttpClient client;
	@Inject
	ResourceLoader loader;
	@Inject
	private HostLmsService hostLmsService;

	SierraPatronsAPIFixture sierraPatronsAPIFixture;

	@BeforeAll
	public void beforeAll(MockServerClient mock) {
		SierraTestUtils.mockFor(mock, sierraHost)
			.setValidCredentials(sierraUser, sierraPass, SIERRA_TOKEN, 60);

		sierraPatronsAPIFixture = new SierraPatronsAPIFixture(mock, loader);
	}

	@Test
	void shouldReportErrorWhenPlacingAPatronRespondsWithInternalServerError() {
		// Arrange
		final var patronPatch = new PatronPatch();
		patronPatch.setUniqueIds(new String[]{"0987654321"});
		sierraPatronsAPIFixture.postPatronErrorResponse("0987654321");
		final var sierraApiClient = createClient();

		// Act
		final var exception = assertThrows(HttpClientResponseException.class,
			() -> Mono.from(sierraApiClient.patrons(patronPatch)).block());

		// Assert
		final var response = exception.getResponse();

		assertThat(response.getStatus(), is(INTERNAL_SERVER_ERROR));
		assertThat(response.code(), is(500));

		final var optionalBody = response.getBody(String.class);

		assertThat(optionalBody.isPresent(), is(true));

		final var body = optionalBody.get();

		assertThat(body, is("Broken"));
	}

	@Test
	void testPostPatron() {
		// Arrange
		final var patronPatch = new PatronPatch();
		patronPatch.setUniqueIds(new String[]{"1234567890"});
		sierraPatronsAPIFixture.postPatronResponse("1234567890");
		final var sierraApiClient = createClient();

		// Act
		var response = Mono.from(sierraApiClient.patrons(patronPatch)).block();

		// Assert
		assertThat(response, is(notNullValue()));
		assertThat(response.getLink(), is("https://sandbox.iii.com/iii/sierra-api/v6/patrons/2745326"));
	}

	@Test
	public void testPatronFind() {
		// Arrange
		var uniqueId = "1234567890";
		sierraPatronsAPIFixture.patronResponseForUniqueId(uniqueId);
		final var sierraApiClient = createClient();

		// Act
		var response = Mono.from(sierraApiClient.patronFind("u", uniqueId)).block();

		// Assert
		assertThat(response, is(notNullValue()));
		assertThat(response.getClass(), is(Result.class));
		assertThat(response.getId(), is(1000002));
	}

	@Test
	public void testPatronFindReturns107() {
		// Arrange
		var uniqueId = "018563984";
		sierraPatronsAPIFixture.patronNotFoundResponseForUniqueId(uniqueId);
		final var sierraApiClient = createClient();

		// Act
		var response = Mono.from( sierraApiClient.patronFind("u", uniqueId) ).block();

		// Assert
		assertThat(response, is(notNullValue()));
		assertThat(response.getClass(), is(Result.class));
		assertThat(response.getId(), is(nullValue()));
	}

	private HostLmsSierraApiClient createClient() {
		final var testHostLms = hostLmsService.findByCode("sierra-items-api-tests").block();

		// Need to create a client directly
		// because injecting gives incorrectly configured client
		return new HostLmsSierraApiClient(testHostLms, client);
	}
}
