package org.olf.dcb.api;

import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.*;
import org.mockserver.client.MockServerClient;
import org.olf.dcb.core.interaction.sierra.SierraApiFixtureProvider;
import org.olf.dcb.core.interaction.sierra.SierraItem;
import org.olf.dcb.core.interaction.sierra.SierraItemsAPIFixture;
import org.olf.dcb.core.interaction.sierra.SierraPatronsAPIFixture;
import org.olf.dcb.core.model.DataHostLms;
import org.olf.dcb.core.model.FunctionalSettingType;
import org.olf.dcb.test.*;
import services.k_int.interaction.sierra.FixedField;
import services.k_int.interaction.sierra.SierraTestUtils;
import services.k_int.interaction.sierra.holds.SierraPatronHold;
import services.k_int.test.mockserver.MockServerMicronautTest;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.micronaut.http.HttpStatus.OK;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

@MockServerMicronautTest
@TestInstance(PER_CLASS)
@Slf4j
class SameLibraryWorkflowApiTests {
	// Injected Dependencies
	@Inject private SierraApiFixtureProvider sierraApiFixtureProvider;
	@Inject private PatronRequestsFixture patronRequestsFixture;
	@Inject private PatronFixture patronFixture;
	@Inject private HostLmsFixture hostLmsFixture;
	@Inject private ClusterRecordFixture clusterRecordFixture;
	@Inject private BibRecordFixture bibRecordFixture;
	@Inject private ReferenceValueMappingFixture referenceValueMappingFixture;
	@Inject private AgencyFixture agencyFixture;
	@Inject private LocationFixture locationFixture;
	@Inject private EventLogFixture eventLogFixture;
	@Inject private TrackingFixture trackingFixture;
	@Inject private PatronRequestApiClient patronRequestApiClient;
	@Inject private AdminApiClient adminApiClient;
	@Inject private ConsortiumFixture consortiumFixture;
	// Sierra API Fixtures
	private SierraPatronsAPIFixture sierraPatronsAPIFixture;
	private SierraItemsAPIFixture sierraItemsAPIFixture;
	// Constants
	private static final String BORROWING_HOST_LMS_CODE = "pr-api-tests-borrowing-agency";
	private static final String BORROWING_BASE_URL = "https://borrower-patron-request-api-tests.com";
	private static final String BORROWING_PATRON_LOCAL_ID = "872321";
	private static final String BORROWING_LOCATION_CODE = "ABC123";
	private static final String BORROWING_AGENCY_CODE = "borrowing-agency";
	private static final String VALID_PICKUP_LOCATION_ID = "0f102b5a-e300-41c8-9aca-afd170e17921";
	private static final String HOME_LIBRARY_CODE = "home-library";
	private static final String TEST_KEY = "key";
	private static final String TEST_SECRET = "secret";
	private static final String COMMON_AVAILABLE_ITEM_LOCAL_ID = "798472";
	private static final Integer COMMON_ITEM_TYPE = 999;
	private static final String COMMON_PATRON_TYPE = "15";
	private static final String BORROWING_ITEM_BARCODE = "6565750674";

	private static DataHostLms BORROWING_HOST_LMS;

	@BeforeAll
	void setUp(MockServerClient mockServerClient) {
		setUpMockCredentials(mockServerClient);
		setUpFixtures(mockServerClient);
		defineAgencies();
		createPickupLocation();
	}

	@BeforeEach
	void resetFixtures() {
		patronRequestsFixture.deleteAll();
		patronFixture.deleteAllPatrons();
		clusterRecordFixture.deleteAll();
		referenceValueMappingFixture.deleteAll();
		eventLogFixture.deleteAll();
		defineMappings();
	}

	@AfterAll
	void tearDown() {
		patronRequestApiClient.removeTokenFromValidTokens();
		consortiumFixture.deleteAll();
	}

	@Test
	void shouldPlaceASameLibraryPatronRequestSuccessfully() {
		log.info("Starting test: shouldPlaceASameLibraryPatronRequestSuccessfully");

		// Arrange
		final UUID clusterRecordId = createClusterRecordWithOneAvailableItem();

		final var localSupplyingHoldId = "407557";
		final var localSupplyingItemId = "2745326";
		final var localSupplyingHoldUrl = "https://borrower-patron-request-api-tests.com/iii/sierra-api/v6/patrons/holds/" + localSupplyingHoldId;

		// Act
		final var placedRequestResponse = patronRequestApiClient.placePatronRequest(
			clusterRecordId, BORROWING_PATRON_LOCAL_ID, VALID_PICKUP_LOCATION_ID,
			BORROWING_HOST_LMS_CODE, HOME_LIBRARY_CODE);

		// Assert
		assertThat(placedRequestResponse.getStatus(), is(OK));

		final var placedPatronRequest = placedRequestResponse.body();
		assertThat(placedPatronRequest, is(notNullValue()));

		final var placedRequestResponseUUID = placedPatronRequest.getId();
		assertThat(placedRequestResponseUUID, is(notNullValue()));
		assertThat(placedRequestResponseUUID, is(Matchers.instanceOf(UUID.class)));

		// Mocks that rely upon the patron request id
		// The borrowing and supplying side are the same here
		mockBorrowingSide(localSupplyingItemId, localSupplyingHoldId, placedRequestResponseUUID, localSupplyingHoldUrl);
			log.debug("PPR {}", placedPatronRequest);
			log.debug("PPRrrr {}", placedRequestResponse);

		assertRequestWasHandedOffAsLocal(placedRequestResponseUUID);
		assertPatronRequestWorkflow(placedRequestResponseUUID);
	}

	// Helper Methods

	private void setUpMockCredentials(MockServerClient mockServerClient) {
		final String TEST_TOKEN = "test-token";
		SierraTestUtils.mockFor(mockServerClient, BORROWING_BASE_URL)
			.setValidCredentials(TEST_KEY, TEST_SECRET, TEST_TOKEN, 60);
	}

	private void setUpFixtures(MockServerClient mockServerClient) {
		locationFixture.deleteAll();
		agencyFixture.deleteAll();
		hostLmsFixture.deleteAll();

		BORROWING_HOST_LMS = hostLmsFixture.createSierraHostLms(BORROWING_HOST_LMS_CODE, TEST_KEY, TEST_SECRET, BORROWING_BASE_URL);

		sierraPatronsAPIFixture = sierraApiFixtureProvider.patronsApiFor(mockServerClient);
		sierraItemsAPIFixture = sierraApiFixtureProvider.itemsApiFor(mockServerClient);

		// allow the borrowing agency and the item agency to be the same
		consortiumFixture.createConsortiumWithFunctionalSetting(FunctionalSettingType.OWN_LIBRARY_BORROWING, true);

		// Mocks for live availability
		sierraItemsAPIFixture.itemsForBibId(COMMON_AVAILABLE_ITEM_LOCAL_ID, List.of(createSierraItem("1000002")));

		// Mocks for patron
		sierraPatronsAPIFixture.addPatronGetExpectation(BORROWING_PATRON_LOCAL_ID);

		// Mocks for local requests
		sierraPatronsAPIFixture.mockPlacePatronHoldRequest(BORROWING_PATRON_LOCAL_ID, "i", null);
	}

	private void mockBorrowingSide(String supplyingItemId, String borrowingHoldId, UUID placedPatronRequestId, String holdUrl) {
		sierraPatronsAPIFixture.mockGetHoldsForPatronReturningSingleItemHold(
			BORROWING_PATRON_LOCAL_ID, holdUrl, "Consortial Hold. tno=" + placedPatronRequestId, supplyingItemId
		);
		sierraItemsAPIFixture.mockGetItemById(supplyingItemId,
			createSierraItem(supplyingItemId)
		);
		sierraPatronsAPIFixture.mockGetHoldById(borrowingHoldId,
			createSierraPatronHold(borrowingHoldId, supplyingItemId)
		);
	}

	private SierraPatronHold createSierraPatronHold(String holdId, String recordId) {
		return SierraPatronHold.builder()
			.id(holdId)
			.recordType("i")
			.record("http://some-record/" + recordId)
			.build();
	}

	private SierraItem createSierraItem(String itemId) {
		final var stringItemType = String.valueOf(COMMON_ITEM_TYPE);

		return SierraItem.builder()
			.id(itemId)
			.statusCode("-")
			.callNumber("BL221 .C48")
			.locationCode(BORROWING_LOCATION_CODE)
			.locationName("King 6th Floor")
			.barcode(BORROWING_ITEM_BARCODE)
			.itemType(stringItemType)
			.fixedFields(Map.of(61, FixedField.builder().value(stringItemType).build()))
			.holdCount(0)
			.build();
		}

	private void defineAgencies() {
		agencyFixture.defineAgency(BORROWING_AGENCY_CODE, "Borrowing Agency", BORROWING_HOST_LMS);
	}

	private void createPickupLocation() {
		locationFixture.createPickupLocation(UUID.fromString(VALID_PICKUP_LOCATION_ID),
			BORROWING_LOCATION_CODE, BORROWING_LOCATION_CODE, agencyFixture.findByCode(BORROWING_AGENCY_CODE));
	}

	private void defineMappings() {
		final int COMMON_PATRON_TYPE_INT = Integer.parseInt(COMMON_PATRON_TYPE);

		// Location to Agency Mappings
		referenceValueMappingFixture.defineLocationToAgencyMapping(BORROWING_HOST_LMS_CODE, "tstce", BORROWING_AGENCY_CODE);
		referenceValueMappingFixture.defineLocationToAgencyMapping(BORROWING_HOST_LMS_CODE, BORROWING_LOCATION_CODE, BORROWING_AGENCY_CODE);

		// Item type mappings
		referenceValueMappingFixture.defineLocalToCanonicalItemTypeRangeMapping(
			BORROWING_HOST_LMS_CODE, COMMON_ITEM_TYPE, COMMON_ITEM_TYPE, "loanable-item");

		// Patron type mappings
		referenceValueMappingFixture.definePatronTypeMapping("DCB", COMMON_PATRON_TYPE,
			BORROWING_HOST_LMS_CODE, COMMON_PATRON_TYPE);
		referenceValueMappingFixture.defineNumericPatronTypeRangeMapping(
			BORROWING_HOST_LMS_CODE, COMMON_PATRON_TYPE_INT, COMMON_PATRON_TYPE_INT, "DCB", COMMON_PATRON_TYPE);
	}

	private UUID createClusterRecordWithOneAvailableItem() {
		final var clusterRecordId = UUID.randomUUID();
		final var clusterRecord = clusterRecordFixture.createClusterRecord(clusterRecordId, clusterRecordId);
		bibRecordFixture.createBibRecord(clusterRecordId, BORROWING_HOST_LMS.id, COMMON_AVAILABLE_ITEM_LOCAL_ID, clusterRecord);

		return clusterRecordId;
	}

	private void assertRequestWasHandedOffAsLocal(UUID requestUUID) {
		final String expectedStatus = "HANDED_OFF_AS_LOCAL";
		final int timeoutInSeconds = 10;

		log.info("Verifying that request ID {} is a single library request...", requestUUID);

		try {
			await()
				.atMost(timeoutInSeconds, SECONDS)
				.until(() -> isRequestHandedOffAsLocal(requestUUID, expectedStatus));

			log.info("Request ID {} successfully handed off as local.", requestUUID);
		} catch (Exception e) {
			log.error("Verification failed for request ID {} within {} seconds.", requestUUID, timeoutInSeconds, e);
			throw new AssertionError("Request was not handed off as local as expected", e);
		}
	}

	private boolean isRequestHandedOffAsLocal(UUID requestUUID, String expectedStatus) {
		var response = adminApiClient.getPatronRequestViaAdminApi(requestUUID);
		String currentStatus = response.getStatus().getCode();
		log.debug("Current status for request ID {}: {}", requestUUID, currentStatus);
		return expectedStatus.equals(currentStatus);
	}

	private void assertPatronRequestWorkflow(UUID placedRequestUUID) {
		final var patronRequest = patronRequestsFixture.findById(placedRequestUUID);

		assertThat(patronRequest, is(notNullValue()));
		assertThat("patron request should use the RET-LOCAL workflow",
			patronRequest.getActiveWorkflow(), is("RET-LOCAL"));
	}

}
