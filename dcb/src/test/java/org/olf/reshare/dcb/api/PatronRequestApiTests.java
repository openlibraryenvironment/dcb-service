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
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.hamcrest.Matcher;
import org.junit.jupiter.api.*;
import org.mockserver.client.MockServerClient;
import org.olf.reshare.dcb.core.HostLmsService;
import org.olf.reshare.dcb.core.interaction.sierra.SierraItemsAPIFixture;
import org.olf.reshare.dcb.request.fulfilment.PatronService;
import org.olf.reshare.dcb.request.fulfilment.PlacePatronRequestCommand;
import org.olf.reshare.dcb.test.BibRecordFixture;
import org.olf.reshare.dcb.test.ClusterRecordFixture;
import org.olf.reshare.dcb.test.DcbTest;
import org.olf.reshare.dcb.test.PatronRequestsFixture;

import io.micronaut.context.annotation.Property;
import io.micronaut.core.io.ResourceLoader;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import lombok.SneakyThrows;
import net.minidev.json.JSONObject;
import services.k_int.interaction.sierra.SierraTestUtils;
import services.k_int.test.mockserver.MockServerMicronautTest;

@DcbTest
@MockServerMicronautTest
@MicronautTest(transactional = false, propertySources = { "classpath:configs/PatronRequestApiTests.yml" }, rebuildContext = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PatronRequestApiTests {
	private static final String SIERRA_TOKEN = "test-token-for-user";

	@Inject
	ResourceLoader loader;

	@Inject
	private PatronRequestsFixture patronRequestsFixture;

	@Inject
	private HostLmsService hostLmsService;

	@Inject
	private ClusterRecordFixture clusterRecordFixture;

	@Inject
	private BibRecordFixture bibRecordFixture;

	@Inject
	private PatronRequestApiClient patronRequestApiClient;

	@Inject
	private PatronService patronService;

	@Inject
	private AdminApiClient adminApiClient;

	@Inject
	@Client("/")
	private HttpClient client;

	// Properties should line up with included property source for the spec.
	@Property(name = "hosts.test1.client.base-url")
	private String sierraHost;

	@Property(name = "hosts.test1.client.key")
	private String sierraUser;

	@Property(name = "hosts.test1.client.secret")
	private String sierraPass;

	@BeforeAll
	@SneakyThrows
	public void addFakeSierraApis(MockServerClient mock) {
		SierraTestUtils.mockFor(mock, sierraHost)
			.setValidCredentials(sierraUser, sierraPass, SIERRA_TOKEN, 60);

		final var sierraItemsAPIFixture = new SierraItemsAPIFixture(mock, loader);

		sierraItemsAPIFixture.twoItemsResponseForBibId("798472");

		sierraItemsAPIFixture.zeroItemsResponseForBibId("565382");
	}

	@BeforeEach
	void beforeEach() {
		patronRequestsFixture.deleteAllPatronRequests();

		bibRecordFixture.deleteAllBibRecords();
		clusterRecordFixture.deleteAllClusterRecords();
	}

	@Test
	@DisplayName("should place patron request given the patron doesn't exist")
	void canPlacePatronRequest() {
		// Arrange
		final var clusterRecordId = randomUUID();
		final var clusterRecord = clusterRecordFixture.createClusterRecord(clusterRecordId);
		final var testHostLms = hostLmsService.findByCode("test1").block();
		final var sourceSystemId = testHostLms.getId();

		bibRecordFixture.createBibRecord(clusterRecordId, sourceSystemId, "798472", clusterRecord);

		// Act
		var placedRequestResponse = patronRequestApiClient.placePatronRequest(clusterRecordId, "43546",
			"RGX12", "ABC123", "test1");

		var fetchedPatronRequest = await().atMost(3, SECONDS)
			.until(() -> adminApiClient.getPatronRequestViaAdminApi(requireNonNull(placedRequestResponse.body()).id()),
				isResolved());

		// Assert
		assertThat(placedRequestResponse.getStatus(), is(OK));

		assertThat(fetchedPatronRequest, is(notNullValue()));
		assertThat(fetchedPatronRequest.citation().bibClusterId(), is(clusterRecordId));
		assertThat(fetchedPatronRequest.pickupLocation().code(), is("ABC123"));
		assertThat(fetchedPatronRequest.requestor().agency().code(), is("RGX12"));
		assertThat(fetchedPatronRequest.status().code(), is("RESOLVED"));
		assertThat(fetchedPatronRequest.supplierRequests(), hasSize(1));
		assertThat(fetchedPatronRequest.requestor().identities(), hasSize(1));

		final var identity = fetchedPatronRequest.requestor().identities().get(0);
		assertThat(identity.homeIdentity(), is(true));
		assertThat(identity.hostLmsCode(), is("test1"));
		assertThat(identity.localId(), is("43546"));

		final var supplierRequest = fetchedPatronRequest.supplierRequests().get(0);
		assertThat(supplierRequest.id(), is(notNullValue()));
		assertThat(supplierRequest.hostLmsCode(), is("test1"));
		assertThat(supplierRequest.item().id(), is(notNullValue()));
	}

	@Test
	@DisplayName("should place patron request with existing patron")
	void shouldPlacePatronRequestWithExistingPatron() {
		// Arrange
		final var clusterRecordId = randomUUID();
		final var clusterRecord = clusterRecordFixture.createClusterRecord(clusterRecordId);
		final var testHostLms = hostLmsService.findByCode("test1").block();
		final var sourceSystemId = testHostLms.getId();
		final var requestor = new PlacePatronRequestCommand.Requestor(new PlacePatronRequestCommand.Agency("RGX12"),
			"43546", "test1");

		bibRecordFixture.createBibRecord(clusterRecordId, sourceSystemId, "798472", clusterRecord);
		patronService.createPatronFor(requestor).block();

		// Act
		final var placedRequestResponse = patronRequestApiClient.placePatronRequest(clusterRecordId, "43546",
			"RGX12", "ABC123", "test1");

		var fetchedPatronRequest = await().atMost(3, SECONDS)
			.until(() -> adminApiClient.getPatronRequestViaAdminApi( requireNonNull(placedRequestResponse.body()).id() ),
				isResolved());

		// Assert
		assertThat(placedRequestResponse.getStatus(), is(OK));

		assertThat(fetchedPatronRequest, is(notNullValue()));
		assertThat(fetchedPatronRequest.citation().bibClusterId(), is(clusterRecordId));
		assertThat(fetchedPatronRequest.pickupLocation().code(), is("ABC123"));
		assertThat(fetchedPatronRequest.requestor().agency().code(), is("RGX12"));
		assertThat(fetchedPatronRequest.status().code(), is("RESOLVED"));
		assertThat(fetchedPatronRequest.supplierRequests(), hasSize(1));
		assertThat(fetchedPatronRequest.requestor().identities(), hasSize(1));

		final var identity = fetchedPatronRequest.requestor().identities().get(0);
		assertThat(identity.homeIdentity(), is(true));
		assertThat(identity.hostLmsCode(), is("test1"));
		assertThat(identity.localId(), is("43546"));

		final var supplierRequest = fetchedPatronRequest.supplierRequests().get(0);
		assertThat(supplierRequest.id(), is(notNullValue()));
		assertThat(supplierRequest.hostLmsCode(), is("test1"));
		assertThat(supplierRequest.item().id(), is(notNullValue()));
	}

	@Test
	void cannotFulfilPatronRequestWhenNoRequestableItemsAreFound() {
		// Arrange
		final var clusterRecordId = randomUUID();
		final var clusterRecord = clusterRecordFixture.createClusterRecord(clusterRecordId);
		final var testHostLms = hostLmsService.findByCode("test1").block();
		final var sourceSystemId = testHostLms.getId();

		bibRecordFixture.createBibRecord(clusterRecordId, sourceSystemId, "565382", clusterRecord);

		// Act
		final var placedRequestResponse = patronRequestApiClient.placePatronRequest(clusterRecordId, "43546",
			"RGX12", "ABC123", "test1");

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
		assertThat(fetchedPatronRequest.requestor().agency().code(), is("RGX12"));
		assertThat(fetchedPatronRequest.status().code(), is("NO_ITEMS_AVAILABLE_AT_ANY_AGENCY"));
		assertThat(fetchedPatronRequest.requestor().identities(), hasSize(1));

		final var identity = fetchedPatronRequest.requestor().identities().get(0);
		assertThat(identity.homeIdentity(), is(true));
		assertThat(identity.hostLmsCode(), is("test1"));
		assertThat(identity.localId(), is("43546"));

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
	void cannotFindPatronRequestForUnknownId() {
		final var exception = assertThrows(HttpClientResponseException.class,
			() -> adminApiClient.getPatronRequestViaAdminApi(randomUUID()));

		final var response = exception.getResponse();

		assertThat(response.getStatus(), is(NOT_FOUND));
	}

	private static Matcher<Object> isResolved() {
		return hasProperty("statusCode", is("RESOLVED"));
	}

	private static Matcher<Object> isNotAvailableToRequest() {
		return hasProperty("statusCode", is("NO_ITEMS_AVAILABLE_AT_ANY_AGENCY"));
	}
}
