package org.olf.dcb.api;

import static io.micronaut.http.HttpStatus.BAD_REQUEST;
import static io.micronaut.http.HttpStatus.NOT_FOUND;
import static io.micronaut.http.HttpStatus.OK;
import static java.util.Objects.requireNonNull;
import static java.util.UUID.randomUUID;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.olf.dcb.core.model.EventType.FAILED_CHECK;
import static org.olf.dcb.core.model.PatronRequest.Status.PATRON_VERIFIED;
import static org.olf.dcb.core.model.PatronRequest.Status.REQUEST_PLACED_AT_BORROWING_AGENCY;
import static org.olf.dcb.core.model.PatronRequest.Status.REQUEST_PLACED_AT_SUPPLYING_AGENCY;
import static org.olf.dcb.core.model.PatronRequest.Status.RESOLVED;
import static org.olf.dcb.core.model.PatronRequest.Status.NO_ITEMS_AVAILABLE_AT_ANY_AGENCY;
import static org.olf.dcb.test.clients.ChecksFailure.Check.hasDescription;

import java.util.List;
import java.util.UUID;

import org.hamcrest.Matcher;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockserver.client.MockServerClient;
import org.olf.dcb.core.interaction.sierra.SierraApiFixtureProvider;
import org.olf.dcb.core.interaction.sierra.SierraItem;
import org.olf.dcb.core.interaction.sierra.SierraPatronsAPIFixture;
import org.olf.dcb.core.model.DataAgency;
import org.olf.dcb.core.model.Event;
import org.olf.dcb.test.AgencyFixture;
import org.olf.dcb.test.BibRecordFixture;
import org.olf.dcb.test.ClusterRecordFixture;
import org.olf.dcb.test.EventLogFixture;
import org.olf.dcb.test.HostLmsFixture;
import org.olf.dcb.test.LocationFixture;
import org.olf.dcb.test.PatronFixture;
import org.olf.dcb.test.PatronRequestsFixture;
import org.olf.dcb.test.ReferenceValueMappingFixture;
import org.olf.dcb.test.clients.ChecksFailure;

import io.micronaut.http.client.exceptions.HttpClientResponseException;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import services.k_int.interaction.sierra.SierraTestUtils;
import services.k_int.interaction.sierra.bibs.BibPatch;
import services.k_int.test.mockserver.MockServerMicronautTest;

@MockServerMicronautTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Slf4j
class PatronRequestApiTests {
	private static final String HOST_LMS_CODE = "patron-request-api-tests";
	private static final String KNOWN_PATRON_LOCAL_ID = "872321";
	private static final String PICKUP_LOCATION_CODE = "ABC123";
	private static final String SUPPLYING_LOCATION_CODE = "ab6";
	private final String SUPPLYING_ITEM_BARCODE = "6565750674";

	@Inject
	private SierraApiFixtureProvider sierraApiFixtureProvider;

	@Inject
	private PatronRequestsFixture patronRequestsFixture;
	@Inject
	private PatronFixture patronFixture;
	@Inject
	private HostLmsFixture hostLmsFixture;
	@Inject
	private ClusterRecordFixture clusterRecordFixture;
	@Inject
	private BibRecordFixture bibRecordFixture;
	@Inject
	private ReferenceValueMappingFixture referenceValueMappingFixture;
	@Inject
	private AgencyFixture agencyFixture;
	@Inject
	private LocationFixture locationFixture;
	@Inject
	private EventLogFixture eventLogFixture;

	@Inject
	private PatronRequestApiClient patronRequestApiClient;
	@Inject
	private AdminApiClient adminApiClient;

	private SierraPatronsAPIFixture sierraPatronsAPIFixture;

	@BeforeAll
	void beforeAll(MockServerClient mockServerClient) {
		final String TOKEN = "test-token";
		final String BASE_URL = "https://patron-request-api-tests.com";
		final String KEY = "patron-request-key";
		final String SECRET = "patron-request-secret";

		SierraTestUtils.mockFor(mockServerClient, BASE_URL)
			.setValidCredentials(KEY, SECRET, TOKEN, 60);

		hostLmsFixture.deleteAll();

		final var h1 = hostLmsFixture.createSierraHostLms(HOST_LMS_CODE, KEY, SECRET, BASE_URL);
		log.debug("Created dataHostLms {}", h1);

		final var h3 = hostLmsFixture.createSierraHostLms("codeBB", KEY, SECRET, BASE_URL);
		log.debug("Created dataHostLms {}", h3);

		final var sierraItemsAPIFixture = sierraApiFixtureProvider.itemsApiFor(mockServerClient);

		this.sierraPatronsAPIFixture = sierraApiFixtureProvider.patronsApiFor(mockServerClient);

		final var sierraBibsAPIFixture = sierraApiFixtureProvider.bibsApiFor(mockServerClient);

		sierraItemsAPIFixture.itemsForBibId("798472", List.of(SierraItem.builder()
			.id("1000002")
			.statusCode("-")
				.callNumber("BL221 .C48")
				.locationCode(SUPPLYING_LOCATION_CODE)
				.locationName("King 6th Floor")
				.barcode(SUPPLYING_ITEM_BARCODE)
				.itemType("999")
				.holdCount(0)
			.build()));

		sierraItemsAPIFixture.getItemById("7916922");

		// patron service
		sierraPatronsAPIFixture.patronNotFoundResponse("u", "872321@ab6");
		sierraPatronsAPIFixture.postPatronResponse("872321@ab6", 2745326);

		// supplying agency service
		sierraPatronsAPIFixture.patronHoldRequestResponse("2745326", "i", null);

		// borrowing agency service
		final var bibPatch = BibPatch.builder()
			.authors(List.of("Stafford Beer"))
			.titles(List.of("Brain of the Firm"))
			.build();

		sierraBibsAPIFixture.createPostBibsMock(bibPatch, 7916920);
		sierraItemsAPIFixture.successResponseForCreateItem(7916920, SUPPLYING_LOCATION_CODE, SUPPLYING_ITEM_BARCODE);
		sierraPatronsAPIFixture.patronHoldRequestResponse(KNOWN_PATRON_LOCAL_ID, "i", null);

		agencyFixture.deleteAll();

		final var da = agencyFixture.saveAgency(DataAgency.builder()
			.id(UUID.randomUUID())
			.code("AGENCY1")
			.name("Test AGENCY1")
			.hostLms(h1)
			.build());
		log.debug("Create dataAgency {}", da);

		agencyFixture.saveAgency(DataAgency.builder()
			.id(UUID.randomUUID())
			.code(SUPPLYING_LOCATION_CODE)
			.name("AB6")
			.hostLms(h3)
			.build());

		sierraPatronsAPIFixture.addPatronGetExpectation("43546");
		sierraPatronsAPIFixture.addPatronGetExpectation(KNOWN_PATRON_LOCAL_ID);

		// AGENCY1 has 1 PICKUP location of PICKUP_LOCATION_CODE (ABC123)
		locationFixture.createPickupLocation(UUID.fromString("0f102b5a-e300-41c8-9aca-afd170e17921"), PICKUP_LOCATION_CODE, PICKUP_LOCATION_CODE, da);
	}

	@BeforeEach
	void beforeEach() {
		patronRequestsFixture.deleteAll();

		patronFixture.deleteAllPatrons();

		clusterRecordFixture.deleteAll();

		referenceValueMappingFixture.deleteAll();

		eventLogFixture.deleteAll();

		referenceValueMappingFixture.defineLocationToAgencyMapping(HOST_LMS_CODE, SUPPLYING_LOCATION_CODE, SUPPLYING_LOCATION_CODE);

		referenceValueMappingFixture.defineLocationToAgencyMapping( HOST_LMS_CODE, "tstce", SUPPLYING_LOCATION_CODE);
		referenceValueMappingFixture.defineLocationToAgencyMapping( HOST_LMS_CODE, "tstr", SUPPLYING_LOCATION_CODE);

		referenceValueMappingFixture.defineLocationToAgencyMapping(HOST_LMS_CODE, PICKUP_LOCATION_CODE, "AGENCY1");
		referenceValueMappingFixture.defineLocationToAgencyMapping(PICKUP_LOCATION_CODE, "AGENCY1");
	}

	@Test
	@DisplayName("should be able to place patron request for new patron")
	void shouldBeAbleToPlaceRequestForNewPatron() {
		log.info("\n\nshouldBeAbleToPlacePatronForNewPatron\n\n");
		// Arrange
		final var clusterRecordId = randomUUID();
		final var clusterRecord = clusterRecordFixture.createClusterRecord(clusterRecordId, clusterRecordId);
		final var hostLms = hostLmsFixture.findByCode(HOST_LMS_CODE);
		final var sourceSystemId = hostLms.getId();

		savePatronTypeMappings();

		bibRecordFixture.createBibRecord(clusterRecordId, sourceSystemId, "798472", clusterRecord);

		// Act
		// We use location UUID for pickup location now and not a  code
		var placedRequestResponse = patronRequestApiClient.placePatronRequest(
			clusterRecordId, KNOWN_PATRON_LOCAL_ID, "0f102b5a-e300-41c8-9aca-afd170e17921", HOST_LMS_CODE, "home-library");

		// Assert
		assertThat(placedRequestResponse.getStatus(), is(OK));

		final var placedPatronRequest = placedRequestResponse.body();

		assertThat(placedPatronRequest, is(notNullValue()));

		// Fix up the sierra mock so that it finds a hold with the right note in it
		// 2745326 will be the identity of this patron in the supplier side system
		log.info("Inserting hold response for patron 2745326 - placedPatronRequest.id=" + placedPatronRequest.getId());
		sierraPatronsAPIFixture.patronHoldResponse("2745326",
				"https://sandbox.iii.com/iii/sierra-api/v6/patrons/holds/407557",
				"Consortial Hold. tno=" + placedPatronRequest.getId());

		// This one is for the borrower side hold
		sierraPatronsAPIFixture.patronHoldResponse(KNOWN_PATRON_LOCAL_ID,
				"https://sandbox.iii.com/iii/sierra-api/v6/patrons/holds/864902",
				"Consortial Hold. tno=" + placedPatronRequest.getId());

		// We need to take the placedRequestResponse and somehow inject it's ID into the
		// patronHolds response message as note="Consortial Hold. tno=UUID"
		// This will ensure that the subsequent lookup can correlate the hold with the
		// request
		// maybe something like sierraPatronsAPIFixture.patronHoldResponse("872321",
		// placedRequestResponse.id);

		assertThat(placedPatronRequest.getRequestor(), is(notNullValue()));
		assertThat(placedPatronRequest.getRequestor().getHomeLibraryCode(), is("home-library"));
		assertThat(placedPatronRequest.getRequestor().getLocalSystemCode(), is(HOST_LMS_CODE));
		assertThat(placedPatronRequest.getRequestor().getLocalId(), is(KNOWN_PATRON_LOCAL_ID));

		log.info("Waiting for placed....");
		AdminApiClient.AdminAccessPatronRequest fetchedPatronRequest = await()
			.atMost(8, SECONDS)
			.until(
				() -> adminApiClient.getPatronRequestViaAdminApi(placedPatronRequest.getId()),
				isPlacedAtBorrowingAgency());

		assertThat(fetchedPatronRequest, is(notNullValue()));

		assertThat(fetchedPatronRequest.getCitation(), is(notNullValue()));
		assertThat(fetchedPatronRequest.getCitation().getBibClusterId(), is(clusterRecordId));

		assertThat(fetchedPatronRequest.getPickupLocation(), is(notNullValue()));
		assertThat(fetchedPatronRequest.getPickupLocation().getCode(), is("0f102b5a-e300-41c8-9aca-afd170e17921"));
		// assertThat(fetchedPatronRequest.getPickupLocation().getCode(), is(PICKUP_LOCATION_CODE));

		assertThat(fetchedPatronRequest.getStatus(), is(notNullValue()));
		assertThat(fetchedPatronRequest.getStatus().getCode(), is("REQUEST_PLACED_AT_BORROWING_AGENCY"));
		assertThat(fetchedPatronRequest.getStatus().getErrorMessage(), is(nullValue()));

		assertThat(fetchedPatronRequest.getLocalRequest().getId(), is("864902"));
		assertThat(fetchedPatronRequest.getLocalRequest().getStatus(), is("PLACED"));
		assertThat(fetchedPatronRequest.getLocalRequest().getItemId(), is("7916922"));
		assertThat(fetchedPatronRequest.getLocalRequest().getBibId(), is("7916920"));

		assertThat(fetchedPatronRequest.getSupplierRequests(), hasSize(1));

		assertThat(fetchedPatronRequest.getRequestor(), is(notNullValue()));
		assertThat(fetchedPatronRequest.getRequestor().getHomeLibraryCode(), is("home-library"));

		assertThat(fetchedPatronRequest.getRequestor().getIdentities(), is(notNullValue()));
		assertThat(fetchedPatronRequest.getRequestor().getIdentities(), hasSize(2));

		final var homeIdentity = fetchedPatronRequest.getRequestor().getIdentities().get(1);

		assertThat(homeIdentity.getLocalId(), is(KNOWN_PATRON_LOCAL_ID));
		assertThat(homeIdentity.getHomeIdentity(), is(true));
		assertThat(homeIdentity.getHostLmsCode(), is(HOST_LMS_CODE));

		final var supplierIdentity = fetchedPatronRequest.getRequestor().getIdentities().get(0);

		assertThat(supplierIdentity.getLocalId(), is("2745326"));
		assertThat(supplierIdentity.getHomeIdentity(), is(false));
		assertThat(supplierIdentity.getHostLmsCode(), is(HOST_LMS_CODE));

		final var supplierRequest = fetchedPatronRequest.getSupplierRequests().get(0);

		assertThat(supplierRequest.getId(), is(notNullValue()));
		assertThat(supplierRequest.getHostLmsCode(), is(HOST_LMS_CODE));
		assertThat(supplierRequest.getStatus(), is("PLACED"));
		assertThat(supplierRequest.getLocalHoldId(), is("407557"));
		assertThat(supplierRequest.getLocalHoldStatus(), is("PLACED"));

		assertThat(supplierRequest.getItem(), is(notNullValue()));
		assertThat(supplierRequest.getItem().getId(), is("1000002"));
		assertThat(supplierRequest.getItem().getLocalItemBarcode(), is(SUPPLYING_ITEM_BARCODE));
		assertThat(supplierRequest.getItem().getLocalItemLocationCode(), is(SUPPLYING_LOCATION_CODE));

		assertThat(fetchedPatronRequest.getAudits(), is(notNullValue()));

		final var lastAuditValue = fetchedPatronRequest.getAudits().size();
		final var lastAudit = fetchedPatronRequest.getAudits().get(lastAuditValue - 1);

		assertThat(lastAudit.getPatronRequestId(), is(fetchedPatronRequest.getId().toString()));
		assertThat(lastAudit.getDescription(), is(nullValue()));
		assertThat(lastAudit.getFromStatus(), is(REQUEST_PLACED_AT_SUPPLYING_AGENCY));
		assertThat(lastAudit.getToStatus(), is(REQUEST_PLACED_AT_BORROWING_AGENCY));
		assertThat(lastAudit.getDate(), is(notNullValue()));

		assertThat("Should not record any failed check event log entries",
			eventLogFixture.findAll(), hasSize(0));
	}

	@Test
	void cannotFulfilPatronRequestWhenNoRequestableItemsAreFound() {
		log.info("\n\ncannotFulfilPatronRequestWhenNoRequestableItemsAreFound\n\n");
		// Arrange
		final var clusterRecordId = randomUUID();
		final var clusterRecord = clusterRecordFixture.createClusterRecord(clusterRecordId, clusterRecordId);
		final var hostLms = hostLmsFixture.findByCode(HOST_LMS_CODE);
		final var sourceSystemId = hostLms.getId();

		bibRecordFixture.createBibRecord(clusterRecordId, sourceSystemId, "565382", clusterRecord);

		savePatronTypeMappings();

		final String requested_pickup_location = "0f102b5a-e300-41c8-9aca-afd170e17921";
		// Act
		final var placedRequestResponse = patronRequestApiClient.placePatronRequest(
			clusterRecordId, "43546", requested_pickup_location, HOST_LMS_CODE, "homeLibraryCode");

		assertThat(placedRequestResponse.getStatus(), is(OK));

		// Need a longer timeout because retrying the Sierra API,
		// which happens when the zero items 404 response is received,
		// takes longer than success
		final var fetchedPatronRequest = await()
			.atMost(12, SECONDS)
			.until(
				() -> adminApiClient.getPatronRequestViaAdminApi(requireNonNull(placedRequestResponse.body()).getId()),
				isNotAvailableToRequest());

		// Assert
		assertThat(fetchedPatronRequest, is(notNullValue()));
		assertThat(fetchedPatronRequest.getCitation(), is(notNullValue()));
		assertThat(fetchedPatronRequest.getCitation().getBibClusterId(), is(clusterRecordId));

		assertThat(fetchedPatronRequest.getPickupLocation(), is(notNullValue()));
		assertThat(fetchedPatronRequest.getPickupLocation().getCode(), is(requested_pickup_location));

		assertThat(fetchedPatronRequest.getStatus(), is(notNullValue()));
		assertThat(fetchedPatronRequest.getStatus().getCode(), is("NO_ITEMS_AVAILABLE_AT_ANY_AGENCY"));
		assertThat(fetchedPatronRequest.getStatus().getErrorMessage(), is(nullValue()));

		assertThat(fetchedPatronRequest.getRequestor(), is(notNullValue()));
		assertThat(fetchedPatronRequest.getRequestor().getIdentities(), hasSize(1));

		final var homeIdentity = fetchedPatronRequest.getRequestor().getIdentities().get(0);
		assertThat(homeIdentity.getHomeIdentity(), is(true));
		assertThat(homeIdentity.getHostLmsCode(), is(HOST_LMS_CODE));
		assertThat(homeIdentity.getLocalId(), is("43546"));

		// No supplier request
		assertThat(fetchedPatronRequest.getSupplierRequests(), is(nullValue()));

		assertThat(fetchedPatronRequest.getAudits(), is(notNullValue()));

		final var lastAuditValue = fetchedPatronRequest.getAudits().size();
		final var lastAudit = fetchedPatronRequest.getAudits().get(lastAuditValue - 1);

		assertThat(lastAudit.getPatronRequestId(), is(fetchedPatronRequest.getId().toString()));
		assertThat(lastAudit.getDescription(), is(nullValue()));
		assertThat(lastAudit.getFromStatus(), is(PATRON_VERIFIED));
		assertThat(lastAudit.getToStatus(), is(NO_ITEMS_AVAILABLE_AT_ANY_AGENCY));
		assertThat(lastAudit.getDate(), is(notNullValue()));
	}

	@Test
	void cannotPlaceRequestWhenNoInformationIsProvided() {
		log.info("\n\ncannotPlaceRequestWhenNoInformationIsProvided\n\n");

		// When placing a request with an empty body
		final var exception = assertThrows(HttpClientResponseException.class,
			() -> patronRequestApiClient.placePatronRequest(null));

		// Then a bad request response should be returned
		final var response = exception.getResponse();
		assertThat("Should return a bad request status", response.getStatus(), is(BAD_REQUEST));
	}

	@Test
	void cannotPlaceRequestForPickupAtUnknownLocation() {
		// Arrange
		final var clusterRecordId = randomUUID();
		final var clusterRecord = clusterRecordFixture.createClusterRecord(clusterRecordId, clusterRecordId);
		final var hostLms = hostLmsFixture.findByCode(HOST_LMS_CODE);
		final var sourceSystemId = hostLms.getId();

		bibRecordFixture.createBibRecord(clusterRecordId, sourceSystemId, "798472", clusterRecord);

		savePatronTypeMappings();

		// Act
		final var exception = assertThrows(HttpClientResponseException.class,
			() -> patronRequestApiClient.placePatronRequest(clusterRecordId,
				KNOWN_PATRON_LOCAL_ID, "unknown-pickup-location", HOST_LMS_CODE, "home-library-code"));

		// Assert
		final var response = exception.getResponse();

		assertThat("Should respond with a bad request status",
			response.getStatus(), is(BAD_REQUEST));

		final var optionalBody = response.getBody(ChecksFailure.class);

		assertThat("Response should have a body", optionalBody.isPresent(), is(true));

		assertThat("Body should report unknown pickup location failed check", optionalBody.get(),
			hasProperty("failedChecks", containsInAnyOrder(
				hasDescription("\"unknown-pickup-location\" is not a recognised pickup location code"),
				hasDescription("Pickup location \"unknown-pickup-location\" is not mapped to an agency"))
			));

		assertThat("Failed checks should be logged", eventLogFixture.findAll(), containsInAnyOrder(
			isFailedCheckEvent("\"unknown-pickup-location\" is not a recognised pickup location code"),
			isFailedCheckEvent("Pickup location \"unknown-pickup-location\" is not mapped to an agency")
		));
	}

	@Test
	void cannotPlaceRequestForPickupAtUnmappedLocation() {
		// Arrange
		final var clusterRecordId = randomUUID();
		final var clusterRecord = clusterRecordFixture.createClusterRecord(clusterRecordId, clusterRecordId);
		final var hostLms = hostLmsFixture.findByCode(HOST_LMS_CODE);
		final var sourceSystemId = hostLms.getId();

		bibRecordFixture.createBibRecord(clusterRecordId, sourceSystemId, "798472", clusterRecord);

		locationFixture.createPickupLocation("Unmapped pickup location", "unmapped-pickup-location");

		savePatronTypeMappings();

		// Act
		final var exception = assertThrows(HttpClientResponseException.class,
			() -> patronRequestApiClient.placePatronRequest(clusterRecordId,
				KNOWN_PATRON_LOCAL_ID, "unmapped-pickup-location", HOST_LMS_CODE, "home-library-code"));

		// Assert
		final var response = exception.getResponse();

		assertThat("Should respond with a bad request status",
			response.getStatus(), is(BAD_REQUEST));

		final var optionalBody = response.getBody(ChecksFailure.class);

		assertThat("Response should have a body", optionalBody.isPresent(), is(true));

		assertThat("Body should report unmapped pickup location failed check", optionalBody.get(),
			hasProperty("failedChecks", containsInAnyOrder(
				hasDescription("Pickup location \"unmapped-pickup-location\" is not mapped to an agency"))
			));

		assertThat("Failed checks should be logged", eventLogFixture.findAll(), containsInAnyOrder(
			isFailedCheckEvent("Pickup location \"unmapped-pickup-location\" is not mapped to an agency")));
	}

	@Test
	void cannotFindPatronRequestForUnknownId() {
		log.info("\n\ncannotFindPatronRequestForUnknownId\n\n");
		final var exception = assertThrows(HttpClientResponseException.class,
				() -> adminApiClient.getPatronRequestViaAdminApi(randomUUID()));

		final var response = exception.getResponse();

		assertThat(response.getStatus(), is(NOT_FOUND));
	}

	private static Matcher<Object> isPlacedAtBorrowingAgency() {
		return hasProperty("status",
			hasProperty("code", is("REQUEST_PLACED_AT_BORROWING_AGENCY")
		));
	}

	private static Matcher<Object> isNotAvailableToRequest() {
		return hasProperty("status",
			hasProperty("code", is("NO_ITEMS_AVAILABLE_AT_ANY_AGENCY")
		));
	}

	private static Matcher<Event> isFailedCheckEvent(String expectedSummary) {
		return allOf(
			hasProperty("id", is(notNullValue())),
			hasProperty("dateCreated", is(notNullValue())),
			hasProperty("type", is(FAILED_CHECK)),
			hasProperty("summary", is(expectedSummary))
		);
	}

	private void savePatronTypeMappings() {
		// We map into and out of patron-request-api-tests here because the tests are naive and not like the real world
		// In a more realistic scenario we go from one context to a different context

		// Define a mapping from patron-request-api-tests:[10-20] to DCB:15 - so any value between 10 and 20 can be mapped
		// to our canonical DCB:15 type
		referenceValueMappingFixture.defineNumericPatronTypeRangeMapping(HOST_LMS_CODE, 10, 20, "DCB", "15");
		// was referenceValueMappingFixture.definePatronTypeMapping("patron-request-api-tests", "15", "DCB", "15");

		// Define a mapping from the spine reference DCB:15 to a TARGET vocal (patron-request-api-tests) value 15
		referenceValueMappingFixture.definePatronTypeMapping("DCB", "15", HOST_LMS_CODE, "15");
	}
}
