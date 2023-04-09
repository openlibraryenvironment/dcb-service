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
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;

import org.hamcrest.Matcher;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockserver.client.MockServerClient;
import org.olf.reshare.dcb.core.HostLmsService;
import org.olf.reshare.dcb.core.interaction.sierra.SierraItemsAPIFixture;
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
	void canPlacePatronRequest() {
		final var clusterRecordId = randomUUID();

		final var clusterRecord = clusterRecordFixture.createClusterRecord(clusterRecordId);

		final var testHostLms = hostLmsService.findByCode("test1").block();

		UUID sourceSystemId = testHostLms.getId();

		bibRecordFixture.createBibRecord(clusterRecordId, sourceSystemId,
			"798472", clusterRecord);

		final var placedRequestResponse = patronRequestApiClient.placePatronRequest(clusterRecordId,
			"jane-smith", "RGX12", "ABC123");

		assertThat(placedRequestResponse.getStatus(), is(OK));

		final var patronRequestId = requireNonNull(placedRequestResponse.body()).id();

		await().atMost(1, SECONDS)
			.until(() -> adminApiClient.getPatronRequestViaAdminApi(patronRequestId), isResolved());

		final var fetchedPatronRequest = adminApiClient.getPatronRequestViaAdminApi(patronRequestId);

		assertThat(fetchedPatronRequest, is(notNullValue()));
		assertThat(fetchedPatronRequest.id(), is(patronRequestId));
		assertThat(fetchedPatronRequest.citation(), is(notNullValue()));
		assertThat(fetchedPatronRequest.citation().bibClusterId(), is(clusterRecordId));
		assertThat(fetchedPatronRequest.requestor(), is(notNullValue()));
		assertThat(fetchedPatronRequest.requestor().identifier(), is("jane-smith"));
		assertThat(fetchedPatronRequest.requestor().agency(), is(notNullValue()));
		assertThat(fetchedPatronRequest.requestor().agency().code(), is("RGX12"));
		assertThat(fetchedPatronRequest.pickupLocation(), is(notNullValue()));
		assertThat(fetchedPatronRequest.pickupLocation().code(), is("ABC123"));
		assertThat(fetchedPatronRequest.status(), is(notNullValue()));
		assertThat(fetchedPatronRequest.status().code(), is("RESOLVED"));

		// supplier request
		assertThat(fetchedPatronRequest.supplierRequests(), is(notNullValue()));
		assertThat(fetchedPatronRequest.supplierRequests(), hasSize(1));

		final var onlySupplierRequest = fetchedPatronRequest.supplierRequests().get(0);

		assertThat(onlySupplierRequest, is(notNullValue()));
		assertThat(onlySupplierRequest.id(), is(notNullValue()));
		assertThat(onlySupplierRequest.hostLmsCode(), is("test1"));
		assertThat(onlySupplierRequest.item().id(), is(notNullValue()));
	}

	@Test
	void cannotFulfilPatronRequestNoAvailableItemsAreFound() {
		final var clusterRecordId = randomUUID();

		final var clusterRecord = clusterRecordFixture.createClusterRecord(clusterRecordId);

		final var testHostLms = hostLmsService.findByCode("test1").block();

		UUID sourceSystemId = testHostLms.getId();

		bibRecordFixture.createBibRecord(clusterRecordId, sourceSystemId,
			"565382", clusterRecord);

		final var placedRequestResponse = patronRequestApiClient.placePatronRequest(clusterRecordId,
			"jane-smith", "RGX12", "ABC123");

		assertThat(placedRequestResponse.getStatus(), is(OK));

		final var patronRequestId = requireNonNull(placedRequestResponse.body()).id();

		// Need a longer timeout because retrying the Sierra API,
		// which happens when the zero items 404 response is received,
		// takes longer than success
		await().atMost(12, SECONDS)
			.until(() -> adminApiClient.getPatronRequestViaAdminApi(patronRequestId),
				isNotAvailableToRequest());

		final var fetchedPatronRequest = adminApiClient.getPatronRequestViaAdminApi(patronRequestId);

		assertThat(fetchedPatronRequest, is(notNullValue()));
		assertThat(fetchedPatronRequest.id(), is(patronRequestId));
		assertThat(fetchedPatronRequest.citation(), is(notNullValue()));
		assertThat(fetchedPatronRequest.citation().bibClusterId(), is(clusterRecordId));
		assertThat(fetchedPatronRequest.requestor(), is(notNullValue()));
		assertThat(fetchedPatronRequest.requestor().identifier(), is("jane-smith"));
		assertThat(fetchedPatronRequest.requestor().agency(), is(notNullValue()));
		assertThat(fetchedPatronRequest.requestor().agency().code(), is("RGX12"));
		assertThat(fetchedPatronRequest.pickupLocation(), is(notNullValue()));
		assertThat(fetchedPatronRequest.pickupLocation().code(), is("ABC123"));
		assertThat(fetchedPatronRequest.status(), is(notNullValue()));
		assertThat(fetchedPatronRequest.status().code(), is("NO_ITEMS_AVAILABLE_AT_ANY_AGENCY"));

		// No supplier request
		assertThat(fetchedPatronRequest.supplierRequests(), is(nullValue()));
	}

	@Test
	void cannotPlaceRequestWhenNoInformationIsProvided() {
		// These are separate variables to only have single invocation in assertThrows
		final var blockingClient = client.toBlocking();
		final var request = HttpRequest.POST("/patrons/requests/place",
			new JSONObject());

		final var exception = assertThrows(HttpClientResponseException.class,
			() -> blockingClient.exchange(request));

		final var response = exception.getResponse();

		assertThat(response.getStatus(), is(BAD_REQUEST));
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
