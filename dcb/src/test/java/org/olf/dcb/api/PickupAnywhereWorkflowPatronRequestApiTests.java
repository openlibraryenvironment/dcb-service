package org.olf.dcb.api;

import static io.micronaut.http.HttpStatus.OK;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.olf.dcb.core.interaction.sierra.SierraBibsAPIFixture.COMMON_BIB_PATCH;
import static org.olf.dcb.core.model.WorkflowConstants.PICKUP_ANYWHERE_WORKFLOW;
import static org.olf.dcb.test.matchers.PatronRequestMatchers.hasActiveWorkflow;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockserver.client.MockServerClient;
import org.olf.dcb.core.interaction.sierra.SierraApiFixtureProvider;
import org.olf.dcb.core.interaction.sierra.SierraBibsAPIFixture;
import org.olf.dcb.core.interaction.sierra.SierraItem;
import org.olf.dcb.core.interaction.sierra.SierraItemsAPIFixture;
import org.olf.dcb.core.interaction.sierra.SierraPatronsAPIFixture;
import org.olf.dcb.core.model.DataHostLms;
import org.olf.dcb.test.AgencyFixture;
import org.olf.dcb.test.BibRecordFixture;
import org.olf.dcb.test.ClusterRecordFixture;
import org.olf.dcb.test.EventLogFixture;
import org.olf.dcb.test.HostLmsFixture;
import org.olf.dcb.test.LocationFixture;
import org.olf.dcb.test.PatronFixture;
import org.olf.dcb.test.PatronRequestsFixture;
import org.olf.dcb.test.ReferenceValueMappingFixture;

import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import services.k_int.interaction.sierra.FixedField;
import services.k_int.interaction.sierra.SierraTestUtils;
import services.k_int.interaction.sierra.holds.SierraPatronHold;
import services.k_int.test.mockserver.MockServerMicronautTest;

@MockServerMicronautTest
@TestInstance(PER_CLASS)
@Slf4j
class PickupAnywhereWorkflowPatronRequestApiTests {
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
	@Inject private PatronRequestApiClient patronRequestApiClient;
	@Inject private AdminApiClient adminApiClient;
	// Sierra API Fixtures
	private SierraPatronsAPIFixture sierraPatronsAPIFixture;
	private SierraItemsAPIFixture sierraItemsAPIFixture;
	private SierraBibsAPIFixture sierraBibsAPIFixture;
	// Constants
	private static final String SUPPLYING_HOST_LMS_CODE = "pr-api-tests-supplying-agency";
	private static final String SUPPLYING_BASE_URL = "https://supplier-patron-request-api-tests.com";
	private static final String BORROWING_HOST_LMS_CODE = "pr-api-tests-borrowing-agency";
	private static final String BORROWING_BASE_URL = "https://borrower-patron-request-api-tests.com";
	private static final String PICKUP_HOST_LMS_CODE = "pr-api-tests-pickup-agency";
	private static final String PICKUP_BASE_URL = "https://pickup-patron-request-api-tests.com";
	private static final String BORROWING_PATRON_LOCAL_ID = "872321";
	private static final String VIRTUAL_PATRON_LOCAL_ID = "2745326";
	private static final String PICKUP_LOCATION_CODE = "ABC123";
	private static final String SUPPLYING_LOCATION_CODE = "ab6";
	private static final String SUPPLYING_AGENCY_CODE = "supplying-agency";
	private static final String BORROWING_AGENCY_CODE = "borrowing-agency";
	private static final String PICKUP_AGENCY_CODE = "pickup-agency";
	private static final String SUPPLYING_ITEM_BARCODE = "6565750674";
	private static final String VALID_PICKUP_LOCATION_ID = "0f102b5a-e300-41c8-9aca-afd170e17921";
	private static final String HOME_LIBRARY_CODE = "home-library";
	private static final String EXPECTED_UNIQUE_ID = "%s@%s".formatted(BORROWING_PATRON_LOCAL_ID, BORROWING_AGENCY_CODE);
	private static final Integer COMMON_VIRTUAL_BIB_ID = 7916920;
	private static final String COMMON_VIRTUAL_LOCAL_ITEM_ID = "7916922";
	private static final String TEST_KEY = "key";
	private static final String TEST_SECRET = "secret";
	private static final String COMMON_AVAILABLE_ITEM_LOCAL_ID = "798472";
	private static final Integer COMMON_ITEM_TYPE = 999;
	private static final String COMMON_PATRON_TYPE = "15";
	private static DataHostLms BORROWING_HOST_LMS;
	private static DataHostLms SUPPLYING_HOST_LMS;
	private static DataHostLms PICKUP_HOST_LMS;

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
	}

	/**
	 * Given a patron places a request (via the API)
	 * and the borrowing agency is different to the pickup agency
	 *
	 * The request should be placed at the pickup agency
	 * The active workflow should be set to RET-PUA
	 */
	@Test
	void shouldPlacePickupAnywherePatronRequest() {
		log.info("Starting test: shouldPlacePickupAnywherePatronRequest");

		// Arrange
		final UUID clusterRecordId = createClusterRecordWithOneAvailableItem();

		final var localSupplyingHoldId = "407557";
		final var localSupplyingItemId = "2745326";
		final var localSupplyingHoldUrl = "https://sandbox.iii.com/iii/sierra-api/v6/patrons/holds/" + localSupplyingHoldId;

		final var localBorrowingHoldId = "864902";
		final var localBorrowingItemId = "2745326";
		final var localBorrowingHoldUrl = "https://sandbox.iii.com/iii/sierra-api/v6/patrons/holds/" + localBorrowingHoldId;

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
		mockSupplyingSide(localSupplyingItemId, localSupplyingHoldId, placedRequestResponseUUID, localSupplyingHoldUrl);
		mockBorrowingSide(localBorrowingItemId, localBorrowingHoldId, placedRequestResponseUUID, localBorrowingHoldUrl);
		mockPickupSide(localSupplyingItemId, localSupplyingHoldId, placedRequestResponseUUID, localSupplyingHoldUrl);

		assertRequestPlacedAtPickupAgency(placedRequestResponseUUID);
		assertPatronRequestUsesPickupAnywhereWorkflow(placedRequestResponseUUID);
	}

	// Helper Methods

	private void setUpMockCredentials(MockServerClient mockServerClient) {
		final String TEST_TOKEN = "test-token";
		SierraTestUtils.mockFor(mockServerClient, BORROWING_BASE_URL)
			.setValidCredentials(TEST_KEY, TEST_SECRET, TEST_TOKEN, 60);
		SierraTestUtils.mockFor(mockServerClient, SUPPLYING_BASE_URL)
			.setValidCredentials(TEST_KEY, TEST_SECRET, TEST_TOKEN, 60);
		SierraTestUtils.mockFor(mockServerClient, PICKUP_BASE_URL)
			.setValidCredentials(TEST_KEY, TEST_SECRET, TEST_TOKEN, 60);
	}

	private void setUpFixtures(MockServerClient mockServerClient) {
		locationFixture.deleteAll();
		agencyFixture.deleteAll();
		hostLmsFixture.deleteAll();

		SUPPLYING_HOST_LMS = hostLmsFixture.createSierraHostLms(SUPPLYING_HOST_LMS_CODE, TEST_KEY, TEST_SECRET, SUPPLYING_BASE_URL);
		BORROWING_HOST_LMS = hostLmsFixture.createSierraHostLms(BORROWING_HOST_LMS_CODE, TEST_KEY, TEST_SECRET, BORROWING_BASE_URL);
		PICKUP_HOST_LMS = hostLmsFixture.createSierraHostLms(PICKUP_HOST_LMS_CODE, TEST_KEY, TEST_SECRET, PICKUP_BASE_URL);

		sierraPatronsAPIFixture = sierraApiFixtureProvider.patronsApiFor(mockServerClient);
		sierraItemsAPIFixture = sierraApiFixtureProvider.itemsApiFor(mockServerClient);
		sierraBibsAPIFixture = sierraApiFixtureProvider.bibsApiFor(mockServerClient);

		// Mocks for live availability
		sierraItemsAPIFixture.itemsForBibId(COMMON_AVAILABLE_ITEM_LOCAL_ID, List.of(createSierraItem("1000002")));

		// Mock borrowing side virtual records
		sierraBibsAPIFixture.createPostBibsMock(COMMON_BIB_PATCH(), COMMON_VIRTUAL_BIB_ID);
		sierraItemsAPIFixture.successResponseForCreateItem(COMMON_VIRTUAL_BIB_ID,
			SUPPLYING_AGENCY_CODE, SUPPLYING_ITEM_BARCODE, COMMON_VIRTUAL_LOCAL_ITEM_ID);
		sierraItemsAPIFixture.mockGetItemById(COMMON_VIRTUAL_LOCAL_ITEM_ID, createSierraItem("1000002"));

		// Mocks for virtual patron
		sierraPatronsAPIFixture.addPatronGetExpectation(BORROWING_PATRON_LOCAL_ID);
		sierraPatronsAPIFixture.patronsQueryNotFoundResponse(EXPECTED_UNIQUE_ID);
		sierraPatronsAPIFixture.postPatronResponse(EXPECTED_UNIQUE_ID, Integer.parseInt(VIRTUAL_PATRON_LOCAL_ID));

		// Mocks for local requests
		sierraPatronsAPIFixture.mockPlacePatronHoldRequest(BORROWING_PATRON_LOCAL_ID, "i", null);
		sierraPatronsAPIFixture.mockPlacePatronHoldRequest(VIRTUAL_PATRON_LOCAL_ID, "i", null);
	}

	private void mockSupplyingSide(String supplyingItemId, String supplyingHoldId, UUID placedPatronRequestId, String holdUrl) {
		sierraPatronsAPIFixture.mockGetHoldsForPatronReturningSingleItemHold(
			VIRTUAL_PATRON_LOCAL_ID, holdUrl, "Consortial Hold. tno=" + placedPatronRequestId, supplyingItemId
		);
		sierraItemsAPIFixture.mockGetItemById(supplyingItemId,
			createSierraItem(supplyingItemId)
		);
		sierraPatronsAPIFixture.mockGetHoldById(supplyingHoldId,
			createSierraPatronHold(supplyingHoldId, supplyingItemId)
		);
	}

	private void mockPickupSide(String supplyingItemId, String supplyingHoldId, UUID placedPatronRequestId, String holdUrl) {
		sierraPatronsAPIFixture.mockGetHoldsForPatronReturningSingleItemHold(
			VIRTUAL_PATRON_LOCAL_ID, holdUrl, "Consortial Hold. tno=" + placedPatronRequestId, supplyingItemId
		);
	}

	private void mockBorrowingSide(String borrowingItemId, String borrowingHoldId, UUID placedPatronRequestId, String holdUrl) {
		sierraPatronsAPIFixture.mockGetHoldsForPatronReturningSingleItemHold(
			BORROWING_PATRON_LOCAL_ID, holdUrl, "Consortial Hold. tno=" + placedPatronRequestId, borrowingItemId
		);
		sierraItemsAPIFixture.mockGetItemById(borrowingItemId,
			createSierraItem(borrowingItemId)
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
			.locationCode(SUPPLYING_LOCATION_CODE)
			.locationName("King 6th Floor")
			.barcode(SUPPLYING_ITEM_BARCODE)
			.itemType(stringItemType)
			.fixedFields(Map.of(61, FixedField.builder().value(stringItemType).build()))
			.holdCount(0)
			.build();
		}

	private void defineAgencies() {
		agencyFixture.defineAgency(SUPPLYING_AGENCY_CODE, "Supplying Agency", SUPPLYING_HOST_LMS);
		agencyFixture.defineAgency(BORROWING_AGENCY_CODE, "Borrowing Agency", BORROWING_HOST_LMS);
		agencyFixture.defineAgency(PICKUP_AGENCY_CODE, "Pickup Agency", PICKUP_HOST_LMS);
	}

	private void createPickupLocation() {
		locationFixture.createPickupLocation(UUID.fromString(VALID_PICKUP_LOCATION_ID),
			PICKUP_LOCATION_CODE, PICKUP_LOCATION_CODE, agencyFixture.findByCode(PICKUP_AGENCY_CODE));
	}

	private void defineMappings() {
		final int COMMON_PATRON_TYPE_INT = Integer.parseInt(COMMON_PATRON_TYPE);

		// Location to Agency Mappings
		referenceValueMappingFixture.defineLocationToAgencyMapping(SUPPLYING_HOST_LMS_CODE, SUPPLYING_LOCATION_CODE, SUPPLYING_AGENCY_CODE);
		referenceValueMappingFixture.defineLocationToAgencyMapping(BORROWING_HOST_LMS_CODE, "tstce", BORROWING_AGENCY_CODE);
		// Item type mappings
		referenceValueMappingFixture.defineLocalToCanonicalItemTypeRangeMapping(
			SUPPLYING_HOST_LMS_CODE, COMMON_ITEM_TYPE, COMMON_ITEM_TYPE, "loanable-item");
		// Patron type mappings
		referenceValueMappingFixture.definePatronTypeMapping("DCB", COMMON_PATRON_TYPE,
			SUPPLYING_HOST_LMS_CODE, COMMON_PATRON_TYPE);
		referenceValueMappingFixture.definePatronTypeMapping("DCB", COMMON_PATRON_TYPE,
			PICKUP_HOST_LMS_CODE, COMMON_PATRON_TYPE);
		referenceValueMappingFixture.defineNumericPatronTypeRangeMapping(
			BORROWING_HOST_LMS_CODE, COMMON_PATRON_TYPE_INT, COMMON_PATRON_TYPE_INT, "DCB", COMMON_PATRON_TYPE);
	}

	private UUID createClusterRecordWithOneAvailableItem() {
		final var clusterRecordId = UUID.randomUUID();
		final var clusterRecord = clusterRecordFixture.createClusterRecord(clusterRecordId, clusterRecordId);
		bibRecordFixture.createBibRecord(clusterRecordId, SUPPLYING_HOST_LMS.id, COMMON_AVAILABLE_ITEM_LOCAL_ID, clusterRecord);

		return clusterRecordId;
	}

	private void assertRequestPlacedAtPickupAgency(UUID requestUUID) {
		final String expectedStatus = "REQUEST_PLACED_AT_PICKUP_AGENCY";
		final int timeoutInSeconds = 15;

		log.info("Verifying that request ID {} is placed at the pickup agency...", requestUUID);

		try {
			await()
				.atMost(timeoutInSeconds, SECONDS)
				.until(() -> isRequestPlacedAtPickupAgency(requestUUID, expectedStatus));

			log.info("Request ID {} successfully placed at the pickup agency.", requestUUID);
		} catch (Exception e) {
			log.error("Verification failed for request ID {} within {} seconds.", requestUUID, timeoutInSeconds, e);
			throw new AssertionError("Request was not placed at the pickup agency as expected", e);
		}
	}

	private boolean isRequestPlacedAtPickupAgency(UUID requestUUID, String expectedStatus) {
		var response = adminApiClient.getPatronRequestViaAdminApi(requestUUID);
		String currentStatus = response.getStatus().getCode();
		log.debug("Current status for request ID {}: {}", requestUUID, currentStatus);
		return expectedStatus.equals(currentStatus);
	}

	private void assertPatronRequestUsesPickupAnywhereWorkflow(UUID placedRequestUUID) {
		final var patronRequest = patronRequestsFixture.findById(placedRequestUUID);

		assertThat("patron request should use pickup anywhere workflow",
			patronRequest, allOf(
				notNullValue(),
				hasActiveWorkflow(PICKUP_ANYWHERE_WORKFLOW)
		));
	}
}
