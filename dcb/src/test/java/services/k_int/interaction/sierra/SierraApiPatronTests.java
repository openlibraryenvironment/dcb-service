package services.k_int.interaction.sierra;

import static io.micronaut.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockserver.client.MockServerClient;
import org.olf.dcb.core.interaction.sierra.SierraPatronsAPIFixture;
import org.olf.dcb.test.HostLmsFixture;

import io.micronaut.core.io.ResourceLoader;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import jakarta.inject.Inject;
import reactor.core.publisher.Mono;
import services.k_int.interaction.sierra.patrons.PatronHoldPost;
import services.k_int.interaction.sierra.patrons.PatronPatch;
import services.k_int.interaction.sierra.patrons.SierraPatronRecord;
import services.k_int.test.mockserver.MockServerMicronautTest;

@MockServerMicronautTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SierraApiPatronTests {
	private static final String HOST_LMS_CODE = "sierra-patron-api-tests";

	@Inject
	private HttpClient client;
	@Inject
	private ResourceLoader loader;
	@Inject
	private HostLmsFixture hostLmsFixture;

	private SierraPatronsAPIFixture sierraPatronsAPIFixture;

	@BeforeAll
	public void beforeAll(MockServerClient mock) {
		final String TOKEN = "test-token";
		final String BASE_URL = "https://patron-api-tests.com";
		final String KEY = "patron-key";
		final String SECRET = "patron-secret";

		SierraTestUtils.mockFor(mock, BASE_URL)
			.setValidCredentials(KEY, SECRET, TOKEN, 60);

		sierraPatronsAPIFixture = new SierraPatronsAPIFixture(mock, loader);

		hostLmsFixture.deleteAllHostLMS();

		hostLmsFixture.createSierraHostLms(KEY, SECRET, BASE_URL, HOST_LMS_CODE);
	}

	@Test
	void shouldReportErrorWhenPlacingAPatronRespondsWithInternalServerError() {
		// Arrange
		final var patronPatch = new PatronPatch();
		patronPatch.setUniqueIds(new String[]{"0987654321"});
		sierraPatronsAPIFixture.postPatronErrorResponse("0987654321");
		final var sierraApiClient = hostLmsFixture.createClient(HOST_LMS_CODE, client);

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
		sierraPatronsAPIFixture.postPatronResponse("1234567890", 2745326);
		final var sierraApiClient = hostLmsFixture.createClient(HOST_LMS_CODE, client);

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
		final var sierraApiClient = hostLmsFixture.createClient(HOST_LMS_CODE, client);

		// Act
		var response = Mono.from(sierraApiClient.patronFind("u", uniqueId)).block();

		// Assert
		assertThat(response, is(notNullValue()));
		assertThat(response.getClass(), is(SierraPatronRecord.class));
		assertThat(response.getId(), is(1000002));
	}

	@Test
	public void testPatronFindReturns107() {
		// Arrange
		final var uniqueId = "018563984";
		sierraPatronsAPIFixture.patronNotFoundResponseForUniqueId(uniqueId);
		final var sierraApiClient = hostLmsFixture.createClient(HOST_LMS_CODE, client);

		// Act
		final var response = singleValueFrom(sierraApiClient.patronFind("u", uniqueId));

		// Assert
		assertThat("Response should be empty", response, is(nullValue()));
	}

	@Test
	void testPatronHoldRequest() {
		// Arrange
		final var patronLocalId = "018563984";
		sierraPatronsAPIFixture.patronHoldResponse(patronLocalId);
		final var sierraApiClient = hostLmsFixture.createClient(HOST_LMS_CODE, client);

		// Act
		var response = Mono.from(sierraApiClient.patronHolds(patronLocalId)).block();

		// Assert
		assertThat(response, is(notNullValue()));
		assertThat(response.entries().get(0), is(notNullValue()));
		assertThat(response.entries().get(0).id(),
			is("https://sandbox.iii.com/iii/sierra-api/v6/patrons/holds/407557"));
	}

	@Test
	void shouldReturnEmptyPublisherWhenReceiveNotFoundError() {
		// Arrange
		final var patronLocalId = "78585745";

		sierraPatronsAPIFixture.patronHoldNotFoundErrorResponse(patronLocalId);

		final var sierraApiClient = hostLmsFixture.createClient(HOST_LMS_CODE, client);

		// Act
		final var response = singleValueFrom(sierraApiClient.patronHolds(patronLocalId));

		// Assert
		assertThat("Response should be empty", response, is(nullValue()));
	}

	@Test
	void testPatronHoldRequestErrorResponse() {
		// Arrange
		final var patronLocalId = "489365810";
		sierraPatronsAPIFixture.patronHoldErrorResponse(patronLocalId);
		final var sierraApiClient = hostLmsFixture.createClient(HOST_LMS_CODE, client);

		// Act
		final var exception = assertThrows(HttpClientResponseException.class,
			() -> Mono.from(sierraApiClient.patronHolds(patronLocalId)).block());

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
	void testPlacePatronHoldRequest() {
		// Arrange
		final var patronLocalId = "1341234";
		sierraPatronsAPIFixture.patronHoldRequestResponse(patronLocalId);

		PatronHoldPost patronHoldPost = new PatronHoldPost();
		patronHoldPost.setRecordNumber(32897458);
		patronHoldPost.setRecordType("i");
		patronHoldPost.setPickupLocation("pickupLocation");

		final var sierraApiClient = hostLmsFixture.createClient(HOST_LMS_CODE, client);

		// Act
		var response = Mono.from(sierraApiClient.placeHoldRequest(patronLocalId, patronHoldPost)).block();

		// Assert
		assertThat(response, is(nullValue()));
	}

	@Test
	void testPlacePatronHoldRequestErrorResponse() {
		// Arrange
		final var patronLocalId = "4325435";
		sierraPatronsAPIFixture.patronHoldRequestErrorResponse(patronLocalId);

		PatronHoldPost patronHoldPost = new PatronHoldPost();
		patronHoldPost.setRecordNumber(423543254);
		patronHoldPost.setRecordType("i");
		patronHoldPost.setPickupLocation("pickupLocation");

		final var sierraApiClient = hostLmsFixture.createClient(HOST_LMS_CODE, client);

		// Act
		final var exception = assertThrows(HttpClientResponseException.class,
			() -> Mono.from(sierraApiClient.placeHoldRequest(patronLocalId, patronHoldPost)).block());

		// Assert
		final var response = exception.getResponse();

		assertThat(response.getStatus(), is(INTERNAL_SERVER_ERROR));
		assertThat(response.code(), is(500));

		final var optionalBody = response.getBody(String.class);

		assertThat(optionalBody.isPresent(), is(true));

		final var body = optionalBody.get();

		assertThat(body, is("Broken"));
	}
}
