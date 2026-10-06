package services.k_int.interaction.sierra;

import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.olf.dcb.core.interaction.sierra.Paths.patronHoldRequestsPath;
import static org.olf.dcb.core.interaction.sierra.Paths.patronHoldsPath;
import static org.olf.dcb.core.interaction.sierra.Paths.patronPath;
import static org.olf.dcb.core.interaction.sierra.Paths.patronsPath;
import static org.olf.dcb.test.IdentifierGenerator.generateBarcode;
import static org.olf.dcb.test.IdentifierGenerator.generateNumericLocalId;
import static org.olf.dcb.test.IdentifierGenerator.generateNumericLocalIdAsString;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;
import static org.olf.dcb.test.matchers.ThrowableMatchers.hasMessage;
import static org.olf.dcb.test.matchers.interaction.HttpResponseProblemMatchers.hasJsonResponseBodyProperty;
import static org.olf.dcb.test.matchers.interaction.HttpResponseProblemMatchers.hasMessageForRequest;
import static org.olf.dcb.test.matchers.interaction.HttpResponseProblemMatchers.hasRequestMethod;
import static org.olf.dcb.test.matchers.interaction.HttpResponseProblemMatchers.hasResponseStatusCode;

import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockserver.client.MockServerClient;
import org.olf.dcb.core.interaction.sierra.HostLmsSierraApiClient;
import org.olf.dcb.core.interaction.sierra.Paths;
import org.olf.dcb.core.interaction.sierra.SierraApiFixtureProvider;
import org.olf.dcb.core.interaction.sierra.SierraPatronsAPIFixture;
import org.olf.dcb.test.HostLmsFixture;
import org.zalando.problem.ThrowableProblem;

import jakarta.inject.Inject;
import services.k_int.interaction.sierra.holds.SierraPatronHold;
import services.k_int.interaction.sierra.holds.SierraPatronHoldResultSet;
import services.k_int.interaction.sierra.patrons.PatronHoldPost;
import services.k_int.interaction.sierra.patrons.PatronPatch;
import services.k_int.interaction.sierra.patrons.SierraPatronRecord;
import services.k_int.test.mockserver.MockServerMicronautTest;

@MockServerMicronautTest
@TestInstance(PER_CLASS)
class SierraApiPatronTests {
	private static final String HOST_LMS_CODE = "sierra-patron-api-tests";
	private static final String ITEM_RECORD_TYPE = "i";
	private static final String SIERRA_BASE_URL = "https://patron-api-tests.com";

	@Inject
	private SierraApiFixtureProvider sierraApiFixtureProvider;

	@Inject
	private HostLmsFixture hostLmsFixture;

	private SierraPatronsAPIFixture sierraPatronsAPIFixture;

	@BeforeAll
	public void beforeAll(MockServerClient mockServerClient) {
		final String TOKEN = "test-token";
		final String KEY = "patron-key";
		final String SECRET = "patron-secret";

		SierraTestUtils.mockFor(mockServerClient, SIERRA_BASE_URL)
			.setValidCredentials(KEY, SECRET, TOKEN, 3600);

		sierraPatronsAPIFixture = sierraApiFixtureProvider.patrons(mockServerClient);

		hostLmsFixture.deleteAll();

		hostLmsFixture.createSierraHostLms(HOST_LMS_CODE, KEY, SECRET, SIERRA_BASE_URL, "item");
	}

	@Test
	void shouldBeAbleToCreatePatron() {
		// Arrange
		final var uniqueId = generateNumericLocalIdAsString();
		final var patronId = generateNumericLocalId();

		sierraPatronsAPIFixture.postPatronResponse(uniqueId, patronId, SIERRA_BASE_URL);

		// Act
		final var patronPatch = PatronPatch.builder()
			.uniqueIds(List.of(uniqueId))
			.build();

		final var sierraApiClient = getClient();

		var response = singleValueFrom(sierraApiClient.patrons(patronPatch));

		// Assert
		assertThat(response, is(notNullValue()));
		assertThat(response.getLink(), is(SIERRA_BASE_URL + patronPath(patronId)));
	}

	@Test
	void shouldReportErrorWhenCreatingAPatronRespondsWithBadRequest() {
		// Arrange
		final var uniqueId = generateNumericLocalIdAsString();

		sierraPatronsAPIFixture.postPatronErrorResponse(uniqueId);

		// Act
		final var patronPatch = PatronPatch.builder()
			.uniqueIds(List.of(uniqueId))
			.build();

		final var sierraApiClient = getClient();

		final var problem = assertThrows(ThrowableProblem.class,
			() -> singleValueFrom(sierraApiClient.patrons(patronPatch)));

		// Assert
		assertThat(problem, allOf(
			hasMessageForRequest("POST", patronsPath()),
			hasResponseStatusCode(400),
			hasJsonResponseBodyProperty("name","Bad JSON/XML Syntax"),
			hasJsonResponseBodyProperty("description",
				"Please check that the JSON fields/values are of the expected JSON data types"),
			hasJsonResponseBodyProperty("code", 130),
			hasJsonResponseBodyProperty("specificCode", 0),
			hasRequestMethod("POST")
		));
	}

	@Test
	public void shouldFindPatronByUniqueId() {
		// Arrange
		final var uniqueId = generateNumericLocalIdAsString();
		final var patronId = generateNumericLocalId();
		final var patronType = 22;
		final var name = "Joe Bloggs";
		final var homeLibraryCode = "testbbb";

		sierraPatronsAPIFixture.patronFoundResponse("u", uniqueId,
			SierraPatronRecord.builder()
				.id(patronId)
				.patronType(patronType)
				.names(List.of(name))
				.homeLibraryCode(homeLibraryCode)
				.build());

		// Act
		final var sierraApiClient = getClient();

		var response = singleValueFrom(sierraApiClient.patronFind("u", uniqueId));

		// Assert
		assertThat("Response should not be null", response, is(notNullValue()));
		assertThat("Should have expected ID", response.getId(), is(patronId));
		assertThat("Should have expected patron type", response.getPatronType(), is(patronType));
		assertThat("Should have expected home library code", response.getHomeLibraryCode(), is(homeLibraryCode));
		assertThat("Should have no barcodes", response.getBarcodes(), is(nullValue()));
	}

	@Test
	public void shouldFindPatronByLocalId() {
		// Arrange
		final var patronId = generateNumericLocalId();
		final var patronBarcode = generateBarcode();
		final var patronType = 15;
		final var homeLibraryCode = "home-library-code";
		final var name = "Bob";

		sierraPatronsAPIFixture.mockGetPatronById(patronId,
			SierraPatronRecord.builder()
				.id(patronId)
				.patronType(patronType)
				.homeLibraryCode(homeLibraryCode)
				.barcodes(List.of(patronBarcode))
				.names(List.of(name))
				.build());

		// Act
		final var sierraApiClient = getClient();

		var response = singleValueFrom(sierraApiClient.getPatron(Long.valueOf(patronId)));

		// Assert
		assertThat("Response should not be null", response, is(notNullValue()));
		assertThat("Should have expected ID", response.getId(), is(patronId));
		assertThat("Should have expected patron type", response.getPatronType(), is(patronType));
		assertThat("Should have expected home library code", response.getHomeLibraryCode(), is(homeLibraryCode));
		assertThat("Should have a barcode", response.getBarcodes(), contains(patronBarcode));
		assertThat("Should have a name", response.getNames(), contains(name));
	}

	@Test
	public void shouldBeEmptyPublisherWhenPatronFindReceivesNotFoundResponse() {
		// Arrange
		final var uniqueId = generateNumericLocalIdAsString();

		sierraPatronsAPIFixture.patronNotFoundResponse("u", uniqueId);

		// Act
		final var sierraApiClient = getClient();

		final var response = singleValueFrom(sierraApiClient.patronFind("u", uniqueId));

		// Assert
		assertThat("Response should be empty", response, is(nullValue()));
	}

	@Test
	void shouldBeAbleToGetHoldsForPatron() {
		// Arrange
		final var patronId = generateNumericLocalIdAsString();
		final var holdIdLink = SIERRA_BASE_URL + patronHoldRequestsPath(patronId);

		sierraPatronsAPIFixture.mockGetHoldsForPatron(patronId,
			SierraPatronHoldResultSet.builder()
				.entries(List.of(
					SierraPatronHold.builder()
						.id(holdIdLink)
						.build()
				))
				.build());

		// Act
		final var sierraApiClient = getClient();

		var response = singleValueFrom(sierraApiClient.patronHolds(patronId));

		// Assert
		assertThat(response, is(notNullValue()));
		assertThat(response.entries().getFirst(), is(notNullValue()));
		assertThat(response.entries().getFirst().id(), is(holdIdLink));
	}

	@Test
	void shouldReturnEmptyPublisherWhenHoldRequestsCannotBeFound() {
		// Arrange
		final var patronId = generateNumericLocalIdAsString();

		sierraPatronsAPIFixture.patronHoldNotFoundErrorResponse(patronId);

		final var sierraApiClient = getClient();

		// Act
		final var response = singleValueFrom(sierraApiClient.patronHolds(patronId));

		// Assert
		assertThat("Response should be empty", response, is(nullValue()));
	}

	@Test
	void shouldHandleErrorWhenFetchingPatronHolds() {
		// Arrange
		final var patronId = generateNumericLocalIdAsString();

		sierraPatronsAPIFixture.patronHoldErrorResponse(patronId);

		// Act
		final var sierraApiClient = getClient();

		final var problem = assertThrows(ThrowableProblem.class,
			() -> singleValueFrom(sierraApiClient.patronHolds(patronId)));

		// Assert
		assertThat(problem, allOf(
			hasMessageForRequest("GET", patronHoldsPath(patronId)),
			hasResponseStatusCode(400),
			hasJsonResponseBodyProperty("name","Bad JSON/XML Syntax"),
			hasJsonResponseBodyProperty("description",
				"Please check that the JSON fields/values are of the expected JSON data types"),
			hasJsonResponseBodyProperty("code", 130),
			hasJsonResponseBodyProperty("specificCode", 0),
			hasRequestMethod("GET")
		));
	}

	@Test
	void shouldBeAbleToPlaceHoldRequest() {
		// Arrange
		final var patronId = generateNumericLocalIdAsString();

		sierraPatronsAPIFixture.mockPlacePatronHoldRequest(patronId, ITEM_RECORD_TYPE, null);

		// Act
		final var patronHoldPost = PatronHoldPost.builder()
			.recordNumber(generateNumericLocalId())
			.recordType(ITEM_RECORD_TYPE)
			.pickupLocation("pickupLocation")
			.build();

		final var sierraApiClient = getClient();

		var response = singleValueFrom(sierraApiClient.placeHoldRequest(patronId, patronHoldPost));

		// Assert
		assertThat(response, is(nullValue()));
	}

	@Test
	void shouldHandleErrorWhenPlacingHold() {
		// Arrange
		final var patronId = generateNumericLocalIdAsString();

		sierraPatronsAPIFixture.patronHoldRequestErrorResponse(patronId, ITEM_RECORD_TYPE);

		// Act
		final var sierraApiClient = getClient();

		final var patronHoldPost = PatronHoldPost.builder()
			.recordNumber(generateNumericLocalId())
			.recordType(ITEM_RECORD_TYPE)
			.pickupLocation("pickupLocation")
			.build();

		final var problem = assertThrows(ThrowableProblem.class,
			() -> singleValueFrom(sierraApiClient.placeHoldRequest(patronId, patronHoldPost)));

		// Assert
		assertThat(problem, hasMessage("Unexpected response from: %s %s"
			.formatted("POST", patronHoldRequestsPath(patronId))));

		assertThat(problem, hasRequestMethod("POST"));
		assertThat(problem, hasResponseStatusCode(500));
		assertThat(problem, hasJsonResponseBodyProperty("code", 109));
		assertThat(problem, hasJsonResponseBodyProperty("description", "Invalid configuration"));
	}

	private HostLmsSierraApiClient getClient() {
		return hostLmsFixture.createLowLevelSierraClient(HOST_LMS_CODE);
	}
}
