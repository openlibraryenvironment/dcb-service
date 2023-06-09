package org.olf.reshare.dcb.api;

import static io.micronaut.http.HttpStatus.BAD_REQUEST;
import static io.micronaut.http.HttpStatus.NOT_FOUND;
import static io.micronaut.http.HttpStatus.OK;
import static java.util.Objects.requireNonNull;
import static java.util.UUID.randomUUID;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.hamcrest.Matcher;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockserver.client.MockServerClient;
import org.olf.reshare.dcb.core.interaction.sierra.SierraBibsAPIFixture;
import org.olf.reshare.dcb.core.interaction.sierra.SierraItemsAPIFixture;
import org.olf.reshare.dcb.core.interaction.sierra.SierraPatronsAPIFixture;
import org.olf.reshare.dcb.request.fulfilment.PatronService;
import org.olf.reshare.dcb.test.BibRecordFixture;
import org.olf.reshare.dcb.test.ClusterRecordFixture;
import org.olf.reshare.dcb.test.HostLmsFixture;
import org.olf.reshare.dcb.test.PatronFixture;
import org.olf.reshare.dcb.test.PatronRequestsFixture;

import io.micronaut.core.io.ResourceLoader;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import jakarta.inject.Inject;
import net.minidev.json.JSONObject;
import services.k_int.interaction.sierra.SierraTestUtils;
import services.k_int.interaction.sierra.bibs.BibPatch;
import services.k_int.test.mockserver.MockServerMicronautTest;

@MockServerMicronautTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PatronRequestApiTests {
	private static final String HOST_LMS_CODE = "patron-request-api-tests";

	@Inject
	ResourceLoader loader;
	@Inject
	private PatronRequestsFixture patronRequestsFixture;
	@Inject
	private PatronFixture patronFixture;
	@Inject
	private ClusterRecordFixture clusterRecordFixture;
	@Inject
	private BibRecordFixture bibRecordFixture;
	@Inject
	private HostLmsFixture hostLmsFixture;
	@Inject
	private PatronRequestApiClient patronRequestApiClient;
	@Inject
	private PatronService patronService;
	@Inject
	private AdminApiClient adminApiClient;
	@Inject
	@Client("/")
	private HttpClient client;

	@BeforeAll
	void beforeAll(MockServerClient mock) {
		final String TOKEN = "test-token";
		final String BASE_URL = "https://patron-request-api-tests.com";
		final String KEY = "patron-request-key";
		final String SECRET = "patron-request-secret";

		SierraTestUtils.mockFor(mock, BASE_URL)
			.setValidCredentials(KEY, SECRET, TOKEN, 60);

		hostLmsFixture.deleteAllHostLMS();

		hostLmsFixture.createSierraHostLms(KEY, SECRET, BASE_URL, HOST_LMS_CODE);

		final var sierraItemsAPIFixture = new SierraItemsAPIFixture(mock, loader);
		final var sierraPatronsAPIFixture = new SierraPatronsAPIFixture(mock, loader);
		final var sierraBibsAPIFixture = new SierraBibsAPIFixture(mock, loader);

		sierraItemsAPIFixture.twoItemsResponseForBibId("798472");
		sierraItemsAPIFixture.zeroItemsResponseForBibId("565382");

		// patron service
		sierraPatronsAPIFixture.patronNotFoundResponseForUniqueId("872321@home-library");
		sierraPatronsAPIFixture.patronNotFoundResponseForUniqueId("43546@home-library");

		sierraPatronsAPIFixture.postPatronResponse("872321@home-library", 2745326);
		sierraPatronsAPIFixture.postPatronResponse("43546@home-library", 6235472);

		// supplying agency service
		sierraPatronsAPIFixture.patronHoldRequestResponse("2745326", 1000002, "ABC123");
		sierraPatronsAPIFixture.patronHoldRequestResponse("6235472", 1000002, "ABC123");

		sierraPatronsAPIFixture.patronHoldResponse("2745326");
		sierraPatronsAPIFixture.patronHoldResponse("6235472");

		// borrowing agency service
		final var bibPatch = BibPatch.builder()
			.authors(new String[]{"Stafford Beer"})
			.titles(new String[]{"Brain of the Firm"})
			.bibCode3("n")
			.build();

		sierraBibsAPIFixture.createPostBibsMock(bibPatch, 7916920);
		sierraItemsAPIFixture.successResponseForCreateItem(7916920, "ab6", "9849123490");
		sierraPatronsAPIFixture.patronHoldRequestResponse("872321", 7916922, "ABC123");
		sierraPatronsAPIFixture.patronHoldResponse("872321");

		sierraBibsAPIFixture.createPostBibsMock(bibPatch, 7916921);
		sierraItemsAPIFixture.successResponseForCreateItem(7916921, "ab6", "9849123490");
		sierraPatronsAPIFixture.patronHoldRequestResponse("43546", 7916922, "ABC123");
		sierraPatronsAPIFixture.patronHoldResponse("43546");

		// Register an expectation that when the client calls /patrons/43546 we respond with the patron record
		sierraPatronsAPIFixture.addPatronGetExpectation(43546L);
		sierraPatronsAPIFixture.addPatronGetExpectation(872321L);
	}

	@BeforeEach
	void beforeEach() {
		patronRequestsFixture.deleteAllPatronRequests();

		patronFixture.deleteAllPatrons();

		bibRecordFixture.deleteAllBibRecords();
		clusterRecordFixture.deleteAllClusterRecords();
	}

	@Test
	@DisplayName("should place patron request given the patron doesn't exist")
	void canPlacePatronRequest() {
		// Arrange
		final var clusterRecordId = randomUUID();
		final var clusterRecord = clusterRecordFixture.createClusterRecord(clusterRecordId);
		final var hostLms = hostLmsFixture.findByCode(HOST_LMS_CODE);
		final var sourceSystemId = hostLms.getId();

		bibRecordFixture.createBibRecord(clusterRecordId, sourceSystemId, "798472", clusterRecord);

		// Act
		var placedRequestResponse = patronRequestApiClient.placePatronRequest(
			clusterRecordId, "872321", "ABC123", HOST_LMS_CODE, "home-library");

		// Assert
		assertThat(placedRequestResponse.getStatus(), is(OK));

		final var placedPatronRequest = placedRequestResponse.body();

		assertThat(placedPatronRequest.requestor(), is(notNullValue()));
		assertThat(placedPatronRequest.requestor().homeLibraryCode(), is("home-library"));
		assertThat(placedPatronRequest.requestor().localSystemCode(), is(HOST_LMS_CODE));
		assertThat(placedPatronRequest.requestor().localId(), is("872321"));

		AdminApiClient.AdminAccessPatronRequest fetchedPatronRequest = await().atMost(5, SECONDS)
			.until(() -> adminApiClient.getPatronRequestViaAdminApi(placedPatronRequest.id()),
				isPlacedAtBorrowingAgency());

		assertThat(fetchedPatronRequest, is(notNullValue()));
		assertThat(fetchedPatronRequest.citation().bibClusterId(), is(clusterRecordId));
		assertThat(fetchedPatronRequest.pickupLocation().code(), is("ABC123"));
		assertThat(fetchedPatronRequest.status().code(), is("REQUEST_PLACED_AT_BORROWING_AGENCY"));
		assertThat(fetchedPatronRequest.localRequest().id(), is("864902"));
		assertThat(fetchedPatronRequest.localRequest().status(), is("PLACED"));
		assertThat(fetchedPatronRequest, is(notNullValue()));
		assertThat(fetchedPatronRequest.supplierRequests(), hasSize(1));

		assertThat(fetchedPatronRequest.requestor(), is(notNullValue()));
		assertThat(fetchedPatronRequest.requestor().homeLibraryCode(), is("home-library"));

		assertThat(fetchedPatronRequest.requestor().identities(), is(notNullValue()));
		assertThat(fetchedPatronRequest.requestor().identities(), hasSize(2));

		final var homeIdentity = fetchedPatronRequest.requestor().identities().get(0);

		assertThat(homeIdentity.homeIdentity(), is(true));
		assertThat(homeIdentity.hostLmsCode(), is(HOST_LMS_CODE));
		assertThat(homeIdentity.localId(), is("872321"));

		final var supplierIdentity = fetchedPatronRequest.requestor().identities().get(1);

		assertThat(supplierIdentity.homeIdentity(), is(false));
		assertThat(supplierIdentity.hostLmsCode(), is(HOST_LMS_CODE));
		assertThat(supplierIdentity.localId(), is("2745326"));

		final var supplierRequest = fetchedPatronRequest.supplierRequests().get(0);

		assertThat(supplierRequest.id(), is(notNullValue()));
		assertThat(supplierRequest.hostLmsCode(), is(HOST_LMS_CODE));
		assertThat(supplierRequest.status(), is("PLACED"));
		assertThat(supplierRequest.localHoldId(), is("407557"));
		assertThat(supplierRequest.localHoldStatus(), is("PLACED"));
		assertThat(supplierRequest.item().id(), is("1000002"));
		assertThat(supplierRequest.item().localItemBarcode(), is("9849123490"));
		assertThat(supplierRequest.item().localItemLocationCode(), is("ab6"));
	}

	@Test
	@DisplayName("should place patron request with existing patron")
	void shouldPlacePatronRequestWithExistingPatron() {
		// Arrange
		final var clusterRecordId = randomUUID();
		final var clusterRecord = clusterRecordFixture.createClusterRecord(clusterRecordId);
		final var hostLms = hostLmsFixture.findByCode(HOST_LMS_CODE);
		final var sourceSystemId = hostLms.getId();

		bibRecordFixture.createBibRecord(clusterRecordId, sourceSystemId, "798472", clusterRecord);

		patronService.createPatron(HOST_LMS_CODE, "43546",
			"home-library").block();

		// Act
		final var placedRequestResponse = patronRequestApiClient.placePatronRequest(
			clusterRecordId, "43546", "ABC123", HOST_LMS_CODE, "home-library");

		// Assert
		assertThat(placedRequestResponse.getStatus(), is(OK));

		final var placedPatronRequest = placedRequestResponse.body();

		assertThat(placedPatronRequest.requestor(), is(notNullValue()));
		assertThat(placedPatronRequest.requestor().homeLibraryCode(), is("home-library"));
		assertThat(placedPatronRequest.requestor().localSystemCode(), is(HOST_LMS_CODE));
		assertThat(placedPatronRequest.requestor().localId(), is("43546"));

		var fetchedPatronRequest = await().atMost(5, SECONDS)
			.until(() -> adminApiClient.getPatronRequestViaAdminApi(requireNonNull(placedRequestResponse.body()).id()),
				isPlacedAtBorrowingAgency());

		assertThat(fetchedPatronRequest, is(notNullValue()));
		assertThat(fetchedPatronRequest.citation().bibClusterId(), is(clusterRecordId));
		assertThat(fetchedPatronRequest.pickupLocation().code(), is("ABC123"));
		assertThat(fetchedPatronRequest.status().code(), is("REQUEST_PLACED_AT_BORROWING_AGENCY"));
		assertThat(fetchedPatronRequest.localRequest().id(), is("864902"));
		assertThat(fetchedPatronRequest.localRequest().status(), is("PLACED"));
		assertThat(fetchedPatronRequest.supplierRequests(), hasSize(1));

		assertThat(fetchedPatronRequest.requestor(), is(notNullValue()));
		assertThat(fetchedPatronRequest.requestor().homeLibraryCode(), is("home-library"));

		assertThat(fetchedPatronRequest.requestor().identities(), is(notNullValue()));
		assertThat(fetchedPatronRequest.requestor().identities(), hasSize(2));

		final var homeIdentity = fetchedPatronRequest.requestor().identities().get(0);

		assertThat(homeIdentity.homeIdentity(), is(true));
		assertThat(homeIdentity.hostLmsCode(), is(HOST_LMS_CODE));
		assertThat(homeIdentity.localId(), is("43546"));

		final var supplierIdentity = fetchedPatronRequest.requestor().identities().get(1);

		assertThat(supplierIdentity.homeIdentity(), is(false));
		assertThat(supplierIdentity.hostLmsCode(), is(HOST_LMS_CODE));
		assertThat(supplierIdentity.localId(), is("6235472"));

		final var supplierRequest = fetchedPatronRequest.supplierRequests().get(0);

		assertThat(supplierRequest.id(), is(notNullValue()));
		assertThat(supplierRequest.hostLmsCode(), is(HOST_LMS_CODE));
		assertThat(supplierRequest.status(), is("PLACED"));
		assertThat(supplierRequest.localHoldId(), is("407557"));
		assertThat(supplierRequest.localHoldStatus(), is("PLACED"));
		assertThat(supplierRequest.item().id(), is("1000002"));
		assertThat(supplierRequest.item().localItemBarcode(), is("9849123490"));
		assertThat(supplierRequest.item().localItemLocationCode(), is("ab6"));
	}


	@Test
	void cannotFulfilPatronRequestWhenNoRequestableItemsAreFound() {
		// Arrange
		final var clusterRecordId = randomUUID();
		final var clusterRecord = clusterRecordFixture.createClusterRecord(clusterRecordId);
		final var hostLms = hostLmsFixture.findByCode(HOST_LMS_CODE);
		final var sourceSystemId = hostLms.getId();

		bibRecordFixture.createBibRecord(clusterRecordId, sourceSystemId, "565382", clusterRecord);

		// Act
		final var placedRequestResponse = patronRequestApiClient.placePatronRequest(clusterRecordId, "43546",
			"ABC123", HOST_LMS_CODE, "homeLibraryCode");

		// Need a longer timeout because retrying the Sierra API,
		// which happens when the zero items 404 response is received,
		// takes longer than success
		final var fetchedPatronRequest = await().atMost(12, SECONDS)
			.until(() -> adminApiClient.getPatronRequestViaAdminApi( requireNonNull(placedRequestResponse.body()).id() ),
				isNotAvailableToRequest());

		// Assert
		assertThat(placedRequestResponse.getStatus(), is(OK));

		assertThat(fetchedPatronRequest, is(notNullValue()));
		assertThat(fetchedPatronRequest.citation().bibClusterId(), is(clusterRecordId));
		assertThat(fetchedPatronRequest.pickupLocation().code(), is("ABC123"));
		assertThat(fetchedPatronRequest.status().code(), is("NO_ITEMS_AVAILABLE_AT_ANY_AGENCY"));
		assertThat(fetchedPatronRequest.requestor().identities(), hasSize(1));

		final var homeIdentity = fetchedPatronRequest.requestor().identities().get(0);
		assertThat(homeIdentity.homeIdentity(), is(true));
		assertThat(homeIdentity.hostLmsCode(), is(HOST_LMS_CODE));
		assertThat(homeIdentity.localId(), is("43546"));

		// No supplier request
		assertThat(fetchedPatronRequest.supplierRequests(), is(nullValue()));
	}

	@Test
	void cannotPlaceRequestWhenNoInformationIsProvided() {
		// Given an empty request body
		final var requestBody = new JSONObject();
		final var request = HttpRequest.POST("/patrons/requests/place", requestBody);

		// When placing a request without providing any information
		final var exception = assertThrows(HttpClientResponseException.class,
			() -> client.toBlocking().exchange(request));

		// Then a bad request response should be returned
		final var response = exception.getResponse();
		assertThat("Should return a bad request status", response.getStatus(), is(BAD_REQUEST));
	}

	@Test
	void cannotPlaceRequestForPatronAtUnknownLocalSystem() {
		// Arrange
		final var clusterRecordId = randomUUID();
		final var clusterRecord = clusterRecordFixture.createClusterRecord(clusterRecordId);
		final var hostLms = hostLmsFixture.findByCode(HOST_LMS_CODE);
		final var sourceSystemId = hostLms.getId();

		bibRecordFixture.createBibRecord(clusterRecordId, sourceSystemId, "798472", clusterRecord);

		// Act
		final var requestBody = new JSONObject() {{
			put("citation", new JSONObject() {{
				put("bibClusterId", clusterRecordId.toString());
			}});
			put("requestor", new JSONObject() {{
				put("localId", "73825");
				put("localSystemCode", "unknown-system");
				put("homeLibraryCode", "home-library-code");
			}});
			put("pickupLocation", new JSONObject() {{
				put("code", "ABC123");
			}});
		}};

		final var request = HttpRequest.POST("/patrons/requests/place", requestBody);

		// When placing a request for a patron at an unknown local system
		final var exception = assertThrows(HttpClientResponseException.class,
			() -> client.toBlocking().exchange(request));

		// Then a bad request response should be returned
		final var response = exception.getResponse();

		assertThat("Should return a bad request status",
			response.getStatus(), is(BAD_REQUEST));

		final var optionalBody = response.getBody(String.class);

		assertThat("Response should have a body",
			optionalBody.isPresent(), is(true));

		assertThat("Body should report no Host LMS found error",
			optionalBody.get(), is("No Host LMS found for code: unknown-system"));
	}

	@Test
	void cannotFindPatronRequestForUnknownId() {
		final var exception = assertThrows(HttpClientResponseException.class,
			() -> adminApiClient.getPatronRequestViaAdminApi(randomUUID()));

		final var response = exception.getResponse();

		assertThat(response.getStatus(), is(NOT_FOUND));
	}

	private static Matcher<Object> isPlacedAtBorrowingAgency() {
		return hasProperty("statusCode", is("REQUEST_PLACED_AT_BORROWING_AGENCY"));
	}

	private static Matcher<Object> isNotAvailableToRequest() {
		return hasProperty("statusCode", is("NO_ITEMS_AVAILABLE_AT_ANY_AGENCY"));
	}
}
