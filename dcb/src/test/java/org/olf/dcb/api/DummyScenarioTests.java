package org.olf.dcb.api;

import static io.micronaut.http.HttpStatus.OK;
import static java.util.UUID.randomUUID;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.olf.dcb.core.model.WorkflowConstants.PICKUP_ANYWHERE_WORKFLOW;
import static org.olf.dcb.core.model.WorkflowConstants.STANDARD_WORKFLOW;
import static org.olf.dcb.core.model.PatronRequest.Status.FINALISED;
import static org.olf.dcb.core.model.PatronRequest.Status.LOANED;
import static org.olf.dcb.core.model.PatronRequest.Status.PICKUP_TRANSIT;
import static org.olf.dcb.core.model.PatronRequest.Status.READY_FOR_PICKUP;
import static org.olf.dcb.core.model.PatronRequest.Status.RECEIVED_AT_PICKUP;
import static org.olf.dcb.core.model.PatronRequest.Status.REQUEST_PLACED_AT_BORROWING_AGENCY;
import static org.olf.dcb.core.model.PatronRequest.Status.REQUEST_PLACED_AT_PICKUP_AGENCY;
import static org.olf.dcb.core.model.PatronRequest.Status.RETURN_TRANSIT;
import static org.olf.dcb.test.matchers.PatronRequestMatchers.hasActiveWorkflow;

import java.util.UUID;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.olf.dcb.core.model.DataAgency;
import org.olf.dcb.core.model.DataHostLms;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.request.fulfilment.RequestWorkflowContextHelper;
import org.olf.dcb.test.AgencyFixture;
import org.olf.dcb.test.BibRecordFixture;
import org.olf.dcb.test.ClusterRecordFixture;
import org.olf.dcb.test.ConsortiumFixture;
import org.olf.dcb.test.EventLogFixture;
import org.olf.dcb.test.HostLmsFixture;
import org.olf.dcb.test.InactiveSupplierRequestsFixture;
import org.olf.dcb.test.LocationFixture;
import org.olf.dcb.test.PatronFixture;
import org.olf.dcb.test.PatronRequestsFixture;
import org.olf.dcb.test.ReferenceValueMappingFixture;
import org.olf.dcb.test.SupplierRequestsFixture;

import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import services.k_int.test.mockserver.MockServerMicronautTest;

@MockServerMicronautTest
@TestInstance(PER_CLASS)
@Slf4j
class DummyScenarioTests {
	@Inject private PatronFixture patronFixture;
	@Inject private PatronRequestsFixture patronRequestsFixture;
	@Inject private SupplierRequestsFixture supplierRequestsFixture;
	@Inject private InactiveSupplierRequestsFixture inactiveSupplierRequestsFixture;
	@Inject private HostLmsFixture hostLmsFixture;
	@Inject private AgencyFixture agencyFixture;
	@Inject private ConsortiumFixture consortiumFixture;
	@Inject private RequestWorkflowContextHelper requestWorkflowContextHelper;
	@Inject private ReferenceValueMappingFixture referenceValueMappingFixture;
	@Inject private LocationFixture locationFixture;
	@Inject private PatronRequestApiClient patronRequestApiClient;
	@Inject private ClusterRecordFixture clusterRecordFixture;
	@Inject private BibRecordFixture bibRecordFixture;
	@Inject private AdminApiClient adminApiClient;
	@Inject private EventLogFixture eventLogFixture;

	private static final String SUPPLYING_HOST_LMS_CODE = "dummy-tests-supplying-agency";
	private static final String SUPPLYING_BASE_URL = "https://supplier-patron-request-api-tests.com";
	private static final String BORROWING_HOST_LMS_CODE = "dummy-tests-borrowing-agency";
	private static final String BORROWING_BASE_URL = "https://borrower-patron-request-api-tests.com";
	private static final String PICKUP_HOST_LMS_CODE = "dummy-tests-pickup-agency";
	private static final String PICKUP_BASE_URL = "https://pickup-patron-request-api-tests.com";
	private static final String BORROWING_PATRON_LOCAL_ID = "872321";
	private static final String VIRTUAL_PATRON_LOCAL_ID = "2745326";
	private static final String PICKUP_LOCATION_CODE = "ABC123";
	private static final String SUPPLYING_LOCATION_CODE = "ab6";
	private static final String SUPPLYING_AGENCY_CODE = "supplying-agency";
	private static final String BORROWING_AGENCY_CODE = "borrowing-agency";
	private static final String PICKUP_AGENCY_CODE = "pickup-agency";
	private static final String SUPPLYING_ITEM_BARCODE = "6565750674";
	private static String PUA_PICKUP_LOCATION_ID;
	private static String STANDARD_PICKUP_LOCATION_ID;
	private static final String HOME_LIBRARY_CODE = "home-library";
	private static final String EXPECTED_UNIQUE_ID = "%s@%s".formatted(BORROWING_PATRON_LOCAL_ID, BORROWING_AGENCY_CODE);
	private static final Integer COMMON_VIRTUAL_BIB_ID = 7916920;
	private static final String COMMON_VIRTUAL_LOCAL_ITEM_ID = "7916922";
	private static final String COMMON_AVAILABLE_ITEM_LOCAL_ID = "798472";
	private static final Integer COMMON_ITEM_TYPE = 999;
	private static final String COMMON_PATRON_TYPE = "15";
	private DataHostLms BORROWING_HOST_LMS;
	private DataHostLms SUPPLYING_HOST_LMS;
	private DataHostLms PICKUP_HOST_LMS;
	private DataAgency borrowingAgency;
	private DataAgency supplyingAgency;
	private DataAgency pickupAgency;

	@BeforeAll
	void setUp() {
		setUpHostLms();
		defineAgencies();

	}

	@BeforeEach
	void beforeEach() {
		clearFixtures();
		defineMappings();
	}

	@AfterAll
	void tearDown() {
		patronRequestApiClient.removeTokenFromValidTokens();
	}

	@Test
	void standardWorkflowScenario() {
		log.info("Starting test: standardWorkflowScenario");

		// Arrange
		STANDARD_PICKUP_LOCATION_ID = randomUUID().toString();
		createStandardPickupLocation();

		final var clusterRecordId = createClusterRecordWithOneAvailableItem();
		final var borrowingPatronLocalId = "872321";
		final var homeLibraryCode = "HOME_LIBRARY_CODE";

		// Act
		final var placedRequestResponse = patronRequestApiClient.placePatronRequest(
			clusterRecordId, borrowingPatronLocalId, STANDARD_PICKUP_LOCATION_ID, BORROWING_HOST_LMS_CODE, homeLibraryCode);

		// Assert
		assertThat(placedRequestResponse.getStatus(), is(OK));

		final var placedPatronRequest = placedRequestResponse.body();
		assertThat(placedPatronRequest, is(notNullValue()));

		final var placedRequestUUID = placedPatronRequest.getId();
		assertThat(placedRequestUUID, is(notNullValue()));
		assertThat(placedRequestUUID, is(Matchers.instanceOf(UUID.class)));

		assertRequestIsInExpectedStatus(placedRequestUUID, REQUEST_PLACED_AT_BORROWING_AGENCY);
		assertPatronRequestUsesWorkflow(placedRequestUUID, STANDARD_WORKFLOW);

		log.info("Circulation starting..");
		log.info("Putting supplier item in transit");

		hostLmsFixture.setDummyState(SUPPLYING_HOST_LMS_CODE, "PICKUP_TRANSIT", "supplier", "RET-STD");
		hostLmsFixture.setDummyState(BORROWING_HOST_LMS_CODE, "PICKUP_TRANSIT", "borrower", "RET-STD");

		patronRequestApiClient.updatePatronRequest(placedRequestUUID);

		assertRequestIsInExpectedStatus(placedRequestUUID, PICKUP_TRANSIT);

		log.info("Pickup location receiving item");

		hostLmsFixture.setDummyState(SUPPLYING_HOST_LMS_CODE, "RECEIVED_AT_PICKUP", "supplier", "RET-STD");
		hostLmsFixture.setDummyState(BORROWING_HOST_LMS_CODE, "RECEIVED_AT_PICKUP", "borrower", "RET-STD");

		patronRequestApiClient.updatePatronRequest(placedRequestUUID);

		assertRequestIsInExpectedStatus(placedRequestUUID, RECEIVED_AT_PICKUP);

		log.info("Item is being put on hold shelf");

		hostLmsFixture.setDummyState(SUPPLYING_HOST_LMS_CODE, "READY_FOR_PICKUP", "supplier", "RET-STD");
		hostLmsFixture.setDummyState(BORROWING_HOST_LMS_CODE, "READY_FOR_PICKUP", "borrower", "RET-STD");

		patronRequestApiClient.updatePatronRequest(placedRequestUUID);

		assertRequestIsInExpectedStatus(placedRequestUUID, READY_FOR_PICKUP);

		log.info("Patron picking up item");

		hostLmsFixture.setDummyState(SUPPLYING_HOST_LMS_CODE, "LOANED", "supplier", "RET-STD");
		hostLmsFixture.setDummyState(BORROWING_HOST_LMS_CODE, "LOANED", "borrower", "RET-STD");

		patronRequestApiClient.updatePatronRequest(placedRequestUUID);

		assertRequestIsInExpectedStatus(placedRequestUUID, LOANED);

		log.info("Patron returned item");

		hostLmsFixture.setDummyState(SUPPLYING_HOST_LMS_CODE, "RETURN_TRANSIT", "supplier", "RET-STD");
		hostLmsFixture.setDummyState(BORROWING_HOST_LMS_CODE, "RETURN_TRANSIT", "borrower", "RET-STD");

		patronRequestApiClient.updatePatronRequest(placedRequestUUID);

		assertRequestIsInExpectedStatus(placedRequestUUID, RETURN_TRANSIT);

		log.info("Patron request completing");

		hostLmsFixture.setDummyState(SUPPLYING_HOST_LMS_CODE, "COMPLETED", "supplier", "RET-STD");
		hostLmsFixture.setDummyState(BORROWING_HOST_LMS_CODE, "COMPLETED", "borrower", "RET-STD");

		patronRequestApiClient.updatePatronRequest(placedRequestUUID);

		// We missed the "COMPLETED" state of the patron request as it happens too quick to catch
		assertRequestIsInExpectedStatus(placedRequestUUID, FINALISED);
	}

	@Test
	void pickupAnywhereWorkflowScenario() {
		log.info("Starting test: pickupAnywhereWorkflowScenario");

		// Arrange
		PUA_PICKUP_LOCATION_ID = randomUUID().toString();
		createPUAPickupLocation();
		final var clusterRecordId = createClusterRecordWithOneAvailableItem();
		final var borrowingPatronLocalId = "872321";
		final var homeLibraryCode = "HOME_LIBRARY_CODE";

		// Act
		final var placedRequestResponse = patronRequestApiClient.placePatronRequest(
			clusterRecordId, borrowingPatronLocalId, PUA_PICKUP_LOCATION_ID, BORROWING_HOST_LMS_CODE, homeLibraryCode);

		// Assert
		assertThat(placedRequestResponse.getStatus(), is(OK));

		final var placedPatronRequest = placedRequestResponse.body();
		assertThat(placedPatronRequest, is(notNullValue()));

		final var placedRequestUUID = placedPatronRequest.getId();
		assertThat(placedRequestUUID, is(notNullValue()));
		assertThat(placedRequestUUID, is(Matchers.instanceOf(UUID.class)));

		assertRequestIsInExpectedStatus(placedRequestUUID, REQUEST_PLACED_AT_PICKUP_AGENCY);
		assertPatronRequestUsesWorkflow(placedRequestUUID, PICKUP_ANYWHERE_WORKFLOW);

		log.info("Circulation starting..");
		log.info("Putting supplier item in transit");

		hostLmsFixture.setDummyState(SUPPLYING_HOST_LMS_CODE, "PICKUP_TRANSIT", "supplier", "RET-PUA");
		hostLmsFixture.setDummyState(PICKUP_HOST_LMS_CODE, "PICKUP_TRANSIT", "pickup", "RET-PUA");
		hostLmsFixture.setDummyState(BORROWING_HOST_LMS_CODE, "PICKUP_TRANSIT", "borrower", "RET-PUA");

		patronRequestApiClient.updatePatronRequest(placedRequestUUID);

		assertRequestIsInExpectedStatus(placedRequestUUID, PICKUP_TRANSIT);

		log.info("Pickup location receiving item");

		hostLmsFixture.setDummyState(SUPPLYING_HOST_LMS_CODE, "RECEIVED_AT_PICKUP", "supplier", "RET-PUA");
		hostLmsFixture.setDummyState(PICKUP_HOST_LMS_CODE, "RECEIVED_AT_PICKUP", "pickup", "RET-PUA");
		hostLmsFixture.setDummyState(BORROWING_HOST_LMS_CODE, "RECEIVED_AT_PICKUP", "borrower", "RET-PUA");

		patronRequestApiClient.updatePatronRequest(placedRequestUUID);

		assertRequestIsInExpectedStatus(placedRequestUUID, RECEIVED_AT_PICKUP);

		log.info("Item is being put on hold shelf");

		hostLmsFixture.setDummyState(SUPPLYING_HOST_LMS_CODE, "READY_FOR_PICKUP", "supplier", "RET-PUA");
		hostLmsFixture.setDummyState(PICKUP_HOST_LMS_CODE, "READY_FOR_PICKUP", "pickup", "RET-PUA");
		hostLmsFixture.setDummyState(BORROWING_HOST_LMS_CODE, "READY_FOR_PICKUP", "borrower", "RET-PUA");

		patronRequestApiClient.updatePatronRequest(placedRequestUUID);

		assertRequestIsInExpectedStatus(placedRequestUUID, READY_FOR_PICKUP);

		log.info("Patron picking up item");

		hostLmsFixture.setDummyState(SUPPLYING_HOST_LMS_CODE, "LOANED", "supplier", "RET-PUA");
		hostLmsFixture.setDummyState(PICKUP_HOST_LMS_CODE, "LOANED", "pickup", "RET-PUA");
		hostLmsFixture.setDummyState(BORROWING_HOST_LMS_CODE, "LOANED", "borrower", "RET-PUA");

		patronRequestApiClient.updatePatronRequest(placedRequestUUID);

		assertRequestIsInExpectedStatus(placedRequestUUID, LOANED);

		log.info("Patron returned item");

		hostLmsFixture.setDummyState(SUPPLYING_HOST_LMS_CODE, "RETURN_TRANSIT", "supplier", "RET-PUA");
		hostLmsFixture.setDummyState(PICKUP_HOST_LMS_CODE, "RETURN_TRANSIT", "pickup", "RET-PUA");
		hostLmsFixture.setDummyState(BORROWING_HOST_LMS_CODE, "RETURN_TRANSIT", "borrower", "RET-PUA");

		patronRequestApiClient.updatePatronRequest(placedRequestUUID);

		assertRequestIsInExpectedStatus(placedRequestUUID, RETURN_TRANSIT);

		log.info("Patron request completing");

		hostLmsFixture.setDummyState(SUPPLYING_HOST_LMS_CODE, "COMPLETED", "supplier", "RET-PUA");
		hostLmsFixture.setDummyState(PICKUP_HOST_LMS_CODE, "COMPLETED", "pickup", "RET-PUA");
		hostLmsFixture.setDummyState(BORROWING_HOST_LMS_CODE, "COMPLETED", "borrower", "RET-PUA");

		patronRequestApiClient.updatePatronRequest(placedRequestUUID);

		// We missed the "COMPLETED" state of the patron request as it happens too quick to catch
		assertRequestIsInExpectedStatus(placedRequestUUID, FINALISED);
	}

	@Test
	void skippedLoanCustomWorkflowScenario() {
		log.info("Starting test: customWorkflowScenario");

		// Arrange
		PUA_PICKUP_LOCATION_ID = randomUUID().toString();
		createPUAPickupLocation();

		final var clusterRecordId = createClusterRecordWithOneAvailableItem();
		final var borrowingPatronLocalId = "872321";
		final var homeLibraryCode = "HOME_LIBRARY_CODE";

		// Act
		final var placedRequestResponse = patronRequestApiClient.placePatronRequest(
			clusterRecordId, borrowingPatronLocalId, PUA_PICKUP_LOCATION_ID, BORROWING_HOST_LMS_CODE, homeLibraryCode);

		// Assert
		assertThat(placedRequestResponse.getStatus(), is(OK));

		final var placedPatronRequest = placedRequestResponse.body();
		assertThat(placedPatronRequest, is(notNullValue()));

		final var placedRequestUUID = placedPatronRequest.getId();
		assertThat(placedRequestUUID, is(notNullValue()));
		assertThat(placedRequestUUID, is(Matchers.instanceOf(UUID.class)));

		assertRequestIsInExpectedStatus(placedRequestUUID, REQUEST_PLACED_AT_PICKUP_AGENCY);
		assertPatronRequestUsesWorkflow(placedRequestUUID, PICKUP_ANYWHERE_WORKFLOW);

		log.info("Circulation starting..");
		log.info("Putting supplier item in transit");

		// first tracking round we should set the state of each hostlms
		hostLmsFixture.setDummyState(SUPPLYING_HOST_LMS_CODE, "PICKUP_TRANSIT", "supplier", "CUSTOM");
		hostLmsFixture.setDummyState(BORROWING_HOST_LMS_CODE, "PICKUP_TRANSIT", "borrower", "CUSTOM");
		hostLmsFixture.setDummyState(PICKUP_HOST_LMS_CODE, "PICKUP_TRANSIT", "pickup", "CUSTOM");

		patronRequestApiClient.updatePatronRequest(placedRequestUUID);

		assertRequestIsInExpectedStatus(placedRequestUUID, PICKUP_TRANSIT);

		log.info("Skipping loan");

		hostLmsFixture.setDummyState(PICKUP_HOST_LMS_CODE, "COMPLETED", "pickup", "CUSTOM");

		patronRequestApiClient.updatePatronRequest(placedRequestUUID);

		assertRequestIsInExpectedStatus(placedRequestUUID, RETURN_TRANSIT);

		log.info("Patron request completing");

		hostLmsFixture.setDummyState(SUPPLYING_HOST_LMS_CODE, "COMPLETED", "supplier", "CUSTOM");

		patronRequestApiClient.updatePatronRequest(placedRequestUUID);

		// We missed the "COMPLETED" state of the patron request as it happens too quick to catch
		assertRequestIsInExpectedStatus(placedRequestUUID, FINALISED);
	}

	// Helper methods

	private void clearFixtures() {
		patronRequestsFixture.deleteAll();
		supplierRequestsFixture.deleteAll();
		patronFixture.deleteAllPatrons();
		clusterRecordFixture.deleteAll();
		referenceValueMappingFixture.deleteAll();
		eventLogFixture.deleteAll();
	}

	private UUID createClusterRecordWithOneAvailableItem() {
		final var clusterRecordId = randomUUID();
		final var clusterRecord = clusterRecordFixture.createClusterRecord(clusterRecordId, clusterRecordId);
		bibRecordFixture.createBibRecord(clusterRecordId, SUPPLYING_HOST_LMS.getId(), COMMON_AVAILABLE_ITEM_LOCAL_ID, clusterRecord);

		return clusterRecordId;
	}

	private void defineAgencies() {
		agencyFixture.defineAgency(SUPPLYING_AGENCY_CODE, "Supplying Agency", SUPPLYING_HOST_LMS);
		agencyFixture.defineAgency(BORROWING_AGENCY_CODE, "Borrowing Agency", BORROWING_HOST_LMS);
		agencyFixture.defineAgency(PICKUP_AGENCY_CODE, "Pickup Agency", PICKUP_HOST_LMS);
	}

	private void createStandardPickupLocation() {
		// Standard
		locationFixture.createPickupLocation(UUID.fromString(STANDARD_PICKUP_LOCATION_ID),
			PICKUP_LOCATION_CODE, PICKUP_LOCATION_CODE, agencyFixture.findByCode(BORROWING_AGENCY_CODE));
	}

	private void createPUAPickupLocation() {
		// PUA
		locationFixture.createPickupLocation(UUID.fromString(PUA_PICKUP_LOCATION_ID),
			PICKUP_LOCATION_CODE, PICKUP_LOCATION_CODE, agencyFixture.findByCode(PICKUP_AGENCY_CODE));
	}

	private void setUpHostLms() {
		hostLmsFixture.deleteAll();
		// Create dummy host LMS instances
		BORROWING_HOST_LMS = hostLmsFixture.createDummyHostLms(BORROWING_HOST_LMS_CODE);
		PICKUP_HOST_LMS = hostLmsFixture.createDummyHostLms(PICKUP_HOST_LMS_CODE);
		SUPPLYING_HOST_LMS = hostLmsFixture.createDummyHostLms(SUPPLYING_HOST_LMS_CODE, SUPPLYING_LOCATION_CODE);
	}

	private void defineMappings() {
		final int COMMON_PATRON_TYPE_INT = Integer.parseInt(COMMON_PATRON_TYPE);

		// Location to Agency Mappings
		referenceValueMappingFixture.defineLocationToAgencyMapping(SUPPLYING_HOST_LMS_CODE, SUPPLYING_LOCATION_CODE, SUPPLYING_AGENCY_CODE);
		referenceValueMappingFixture.defineLocationToAgencyMapping(BORROWING_HOST_LMS_CODE, "TR", BORROWING_AGENCY_CODE);
		// Item type mappings
		referenceValueMappingFixture.defineLocalToCanonicalItemTypeRangeMapping(
			SUPPLYING_HOST_LMS_CODE, COMMON_ITEM_TYPE, COMMON_ITEM_TYPE, "loanable-item");
		// Patron type mappings
		referenceValueMappingFixture.definePatronTypeMapping("DCB", COMMON_PATRON_TYPE,
			SUPPLYING_HOST_LMS_CODE, COMMON_PATRON_TYPE);
		referenceValueMappingFixture.definePatronTypeMapping("DCB", COMMON_PATRON_TYPE,
			PICKUP_HOST_LMS_CODE, COMMON_PATRON_TYPE);

		// Dummy hostlms has a static STD value as local patron type
		referenceValueMappingFixture.definePatronTypeMapping(BORROWING_HOST_LMS_CODE, "STD",
			"DCB", COMMON_PATRON_TYPE);

		referenceValueMappingFixture.defineNumericPatronTypeRangeMapping(
			BORROWING_HOST_LMS_CODE, COMMON_PATRON_TYPE_INT, COMMON_PATRON_TYPE_INT, "DCB", COMMON_PATRON_TYPE);
	}

	private void assertRequestIsInExpectedStatus(UUID requestUUID, PatronRequest.Status expectedStatus) {
		log.info("Verifying that request ID {} is in expected status {}...", requestUUID, expectedStatus);

		int timeoutInSeconds = 10;

		try {
			await()
				.atMost(timeoutInSeconds, SECONDS)
				.until(() -> {
					final var response = patronRequestsFixture.findById(requestUUID);
					final var currentStatus = response.getStatus();
					log.debug("Current status for request ID {}: {}", requestUUID, currentStatus);
					return expectedStatus.equals(currentStatus);
				});

			log.info("Request ID {} successfully in expected status {}.", requestUUID, expectedStatus);
		} catch (Exception e) {
			log.error("Verification failed for request ID {} within {} seconds.", requestUUID, timeoutInSeconds, e);
			throw new AssertionError("Request was not in expected status as expected", e);
		}
	}

	private void assertPatronRequestUsesWorkflow(UUID placedRequestUUID, String expectedWorkflow) {
		final var patronRequest = patronRequestsFixture.findById(placedRequestUUID);

		assertThat("patron request should use %s workflow".formatted(expectedWorkflow),
			patronRequest, allOf(
				notNullValue(),
				hasActiveWorkflow(expectedWorkflow)
			));
	}
}
