package org.olf.dcb.request.workflow;

import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.olf.dcb.core.interaction.HostLmsRequest.HOLD_CONFIRMED;
import static org.olf.dcb.core.interaction.HostLmsRequest.HOLD_MISSING;
import static org.olf.dcb.core.model.PatronRequest.Status.CANCELLED;
import static org.olf.dcb.core.model.PatronRequest.Status.REQUEST_PLACED_AT_BORROWING_AGENCY;
import static org.olf.dcb.core.model.PatronRequest.Status.SUBMITTED_TO_DCB;
import static org.olf.dcb.core.model.WorkflowConstants.PICKUP_ANYWHERE_WORKFLOW;
import static org.olf.dcb.core.model.WorkflowConstants.STANDARD_WORKFLOW;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;
import static org.olf.dcb.test.matchers.PatronRequestMatchers.hasStatus;
import static org.olf.dcb.test.matchers.SupplierRequestMatchers.hasStatusCode;
import static org.olf.dcb.utils.CollectionUtils.mapStream;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockserver.client.MockServerClient;
import org.olf.dcb.core.interaction.sierra.SierraApiFixtureProvider;
import org.olf.dcb.core.interaction.sierra.SierraPatronsAPIFixture;
import org.olf.dcb.core.model.DataAgency;
import org.olf.dcb.core.model.DataHostLms;
import org.olf.dcb.core.model.Patron;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.PatronRequestAudit;
import org.olf.dcb.core.model.SupplierRequest;
import org.olf.dcb.request.fulfilment.RequestWorkflowContextHelper;
import org.olf.dcb.request.fulfilment.SupplierRequestStatusCode;
import org.olf.dcb.storage.PatronRequestAuditRepository;
import org.olf.dcb.test.AgencyFixture;
import org.olf.dcb.test.HostLmsFixture;
import org.olf.dcb.test.LocationFixture;
import org.olf.dcb.test.PatronFixture;
import org.olf.dcb.test.PatronRequestsFixture;
import org.olf.dcb.test.ReferenceValueMappingFixture;
import org.olf.dcb.test.SupplierRequestsFixture;

import jakarta.inject.Inject;
import reactor.core.publisher.Mono;
import services.k_int.interaction.sierra.SierraCodeTuple;
import services.k_int.interaction.sierra.SierraTestUtils;
import services.k_int.interaction.sierra.holds.SierraPatronHold;
import services.k_int.test.mockserver.MockServerMicronautTest;

@MockServerMicronautTest
@TestInstance(PER_CLASS)
class CancelledPatronRequestTransitionTests {

	private static final String SUPPLYING_HOST_LMS_CODE = "supplier-host-lms";
	private static final String PICKUP_HOST_LMS_CODE = "pickup-host-lms";

	private static final String PICKUP_AGENCY_CODE = "pickup-agency";
	private static final String VALID_PICKUP_LOCATION_ID = "0f102b5a-e300-41c8-9aca-afd170e17921";
	private static final String PICKUP_LOCATION_CODE = "ABC123";

	@Inject
	private SierraApiFixtureProvider sierraApiFixtureProvider;
	@Inject
	private HostLmsFixture hostLmsFixture;
	@Inject
	private AgencyFixture agencyFixture;
	@Inject
	private PatronFixture patronFixture;
	@Inject
	private PatronRequestsFixture patronRequestsFixture;
	@Inject
	private SupplierRequestsFixture supplierRequestsFixture;
	private SierraPatronsAPIFixture sierraPatronsAPIFixture;
	@Inject
	private RequestWorkflowContextHelper requestWorkflowContextHelper;
	@Inject
	private PatronRequestWorkflowService patronRequestWorkflowService;
	@Inject
	private CancelledPatronRequestTransition cancelledPatronRequestTransition;
	@Inject
	private PatronRequestAuditRepository patronRequestAuditRepository;
	@Inject
	private ReferenceValueMappingFixture referenceValueMappingFixture;
	@Inject
	private LocationFixture locationFixture;

	private DataAgency pickupAgency;
	private DataHostLms supplierHostLMS;
	private DataHostLms pickupHostLMS;
	private String BASE_URL;

	@BeforeAll
	void beforeAll(MockServerClient mockServerClient) {
		final String TOKEN = "test-token";
		BASE_URL = "https://borrowing-agency-service-tests.com";
		final String KEY = "borrowing-agency-service-key";
		final String SECRET = "borrowing-agency-service-secret";

		hostLmsFixture.deleteAll();
		agencyFixture.deleteAll();

		SierraTestUtils.mockFor(mockServerClient, BASE_URL)
			.setValidCredentials(KEY, SECRET, TOKEN, 60);

		supplierHostLMS = hostLmsFixture.createSierraHostLms(SUPPLYING_HOST_LMS_CODE, KEY,
			SECRET, BASE_URL, "title");

		pickupHostLMS = hostLmsFixture.createSierraHostLms(PICKUP_HOST_LMS_CODE, KEY,
			SECRET, BASE_URL, "title");

		pickupAgency = agencyFixture.defineAgency(PICKUP_AGENCY_CODE, "Pickup Agency", pickupHostLMS);

		sierraPatronsAPIFixture = sierraApiFixtureProvider.patrons(mockServerClient, null);
	}

	@BeforeEach
	void beforeEach() {
		supplierRequestsFixture.deleteAll();
		patronRequestsFixture.deleteAll();
		patronFixture.deleteAllPatrons();
	}

	@Test
	void shouldNotProgressPatronRequestToCancelledWhenNotApplicable() {
		// Arrange
		final var patron = Patron.builder()
			.id(randomUUID())
			.build();

		patronFixture.savePatron(patron);

		final var patronRequest = PatronRequest.builder()
			.id(randomUUID())
			.patron(patron)
			.status(SUBMITTED_TO_DCB)
			.build();

		patronRequestsFixture.savePatronRequest(patronRequest);

		final var localSupplyingHoldId = "7357356";

		supplierRequestsFixture.saveSupplierRequest(
			SupplierRequest.builder()
				.id(randomUUID())
				.localStatus(HOLD_CONFIRMED)
				.localId(localSupplyingHoldId)
				.localItemId("647375678")
				.localItemBarcode("26123553")
				.patronRequest(patronRequest)
				.hostLmsCode(SUPPLYING_HOST_LMS_CODE)
				.build());

		sierraPatronsAPIFixture.mockDeleteHold(localSupplyingHoldId);

		// Act
		final var applicable = singleValueFrom(
			requestWorkflowContextHelper.fromPatronRequest(patronRequest)
				.map(ctx -> cancelledPatronRequestTransition.isApplicableFor(ctx)));

		// Assert
		assertThat("Should not be applicable for a submitted DCB status",
			applicable, is(false));

	}

	@Test
	void shouldProgressPatronRequestToCancelled() {
		// Arrange
		final var patron = Patron.builder()
			.id(randomUUID())
			.build();

		patronFixture.savePatron(patron);
		final var virtualPatronIdentity = patronFixture.saveIdentityAndReturn(patron, supplierHostLMS, "007",
			false, "-", "LOCAL_SYSTEM_CODE", null);

		final var patronRequest = PatronRequest.builder()
			.id(randomUUID())
			.patron(patron)
			.status(REQUEST_PLACED_AT_BORROWING_AGENCY)
			.localRequestStatus(HOLD_MISSING)
			.activeWorkflow(STANDARD_WORKFLOW)
			.build();

		patronRequestsFixture.savePatronRequest(patronRequest);

		final var localSupplyingHoldId = "7357357";

		supplierRequestsFixture.saveSupplierRequest(
			SupplierRequest.builder()
				.id(randomUUID())
				.localStatus(HOLD_CONFIRMED)
				.localId(localSupplyingHoldId)
				.localItemId("647375678")
				.localItemBarcode("26123553")
				.patronRequest(patronRequest)
				.statusCode(SupplierRequestStatusCode.PLACED)
				.hostLmsCode(SUPPLYING_HOST_LMS_CODE)
				.virtualIdentity(virtualPatronIdentity)
				.build());

		sierraPatronsAPIFixture.mockGetHoldById(localSupplyingHoldId, SierraPatronHold.builder()
			.id("%s/iii/sierra-api/v6/patrons/holds/%s".formatted(BASE_URL, localSupplyingHoldId))
			.build());

		sierraPatronsAPIFixture.mockDeleteHold(localSupplyingHoldId);

		// Act
		final var updatedPatronRequest = attemptCancelledPatronRequestTransition(patronRequest);

		// Assert
		assertThat(updatedPatronRequest, allOf(
			notNullValue(),
			hasStatus(CANCELLED)
		));

		sierraPatronsAPIFixture.verifyDeleteHoldRequestMade(localSupplyingHoldId);
	}

	@Test
	void shouldProgressPatronRequestToCancelledForPickupAnywhereRequests() {
		// Arrange
		final var patron = Patron.builder()
			.id(randomUUID())
			.build();

		patronFixture.savePatron(patron);
		final var virtualPatronIdentity = patronFixture.saveIdentityAndReturn(patron, supplierHostLMS, "007",
			false, "-", "LOCAL_SYSTEM_CODE", null);

		final var pickupPatronIdentity = patronFixture.saveIdentityAndReturn(patron, pickupHostLMS, "008",
			false, "-", "LOCAL_SYSTEM_CODE", null);

		final var localPickupHoldId = "87598";

		final var patronRequest = PatronRequest.builder()
			.id(randomUUID())
			.patron(patron)
			.status(REQUEST_PLACED_AT_BORROWING_AGENCY)
			.localRequestStatus(HOLD_MISSING)
			.activeWorkflow(PICKUP_ANYWHERE_WORKFLOW)
			// pickup request info
			.pickupItemId("4857934")
			.pickupRequestId(localPickupHoldId)
			.pickupPatronId(pickupPatronIdentity.getLocalId())
			.pickupLocationCode(VALID_PICKUP_LOCATION_ID)
			.build();

		patronRequestsFixture.savePatronRequest(patronRequest);

		final var localSupplyingHoldId = "5436344";

		supplierRequestsFixture.saveSupplierRequest(
			SupplierRequest.builder()
				.id(randomUUID())
				.localStatus(HOLD_CONFIRMED)
				.localId(localSupplyingHoldId)
				.localItemId("647375678")
				.localItemBarcode("26123553")
				.patronRequest(patronRequest)
				.statusCode(SupplierRequestStatusCode.PLACED)
				.hostLmsCode(SUPPLYING_HOST_LMS_CODE)
				.virtualIdentity(virtualPatronIdentity)
				.build());

		sierraPatronsAPIFixture.mockGetHoldById(localSupplyingHoldId, SierraPatronHold.builder()
			.id("%s/iii/sierra-api/v6/patrons/holds/%s".formatted(BASE_URL, localSupplyingHoldId))
			.build());

		sierraPatronsAPIFixture.mockGetHoldById(localPickupHoldId, SierraPatronHold.builder()
			.id("%s/iii/sierra-api/v6/patrons/holds/%s".formatted(BASE_URL, localPickupHoldId))
			.build());

		sierraPatronsAPIFixture.mockDeleteHold(localSupplyingHoldId);
		sierraPatronsAPIFixture.mockDeleteHold(localPickupHoldId);

		locationFixture.createPickupLocation(UUID.fromString(VALID_PICKUP_LOCATION_ID),
			PICKUP_LOCATION_CODE, PICKUP_LOCATION_CODE, pickupAgency);

		referenceValueMappingFixture.defineLocationToAgencyMapping(PICKUP_HOST_LMS_CODE, PICKUP_LOCATION_CODE,
			PICKUP_AGENCY_CODE);

		// Act
		final var updatedPatronRequest = attemptCancelledPatronRequestTransition(patronRequest);

		// Assert
		assertThat(updatedPatronRequest, allOf(
			notNullValue(),
			hasStatus(CANCELLED)
		));

		sierraPatronsAPIFixture.verifyDeleteHoldRequestMade(localSupplyingHoldId);
		sierraPatronsAPIFixture.verifyDeleteHoldRequestMade(localPickupHoldId);
	}

	@Test
	void shouldProgressPatronRequestToCancelledWhenNotYetLoanedAndMissingLocalHoldWithTrueVerification() {
		// Arrange
		final var patron = Patron.builder()
			.id(randomUUID())
			.patronIdentities(List.of())
			.build();

		patronFixture.savePatron(patron);
		final var virtualPatronIdentity = patronFixture.saveIdentityAndReturn(patron, supplierHostLMS, "007",
			false, "-", "LOCAL_SYSTEM_CODE", null);

		final var patronRequest = PatronRequest.builder()
			.id(randomUUID())
			.patron(patron)
			.status(REQUEST_PLACED_AT_BORROWING_AGENCY)
			.localRequestStatus(HOLD_MISSING)
			.build();

		patronRequestsFixture.savePatronRequest(patronRequest);

		final var localSupplyingHoldId = "7357358";
		final var originalLocalItemId = "647375678";

		supplierRequestsFixture.saveSupplierRequest(
			SupplierRequest.builder()
				.id(randomUUID())
				.localStatus(HOLD_CONFIRMED)
				.localId(localSupplyingHoldId)
				.localItemId(originalLocalItemId)
				.localItemBarcode("26123553")
				.patronRequest(patronRequest)
				.hostLmsCode(SUPPLYING_HOST_LMS_CODE)
				.virtualIdentity(virtualPatronIdentity)
				.build());

		sierraPatronsAPIFixture.mockDeleteHold(localSupplyingHoldId);
		sierraPatronsAPIFixture.mockGetHoldById(localSupplyingHoldId, SierraPatronHold.builder()
			.id(localSupplyingHoldId)
			.status(SierraCodeTuple.builder().name("Missing").code("m").build())
			.build());

		// Act
		final var updatedPatronRequest = attemptCancelledPatronRequestTransition(patronRequest);

		// Assert
		assertThat(updatedPatronRequest, allOf(
			notNullValue(),
			hasStatus(CANCELLED)
		));

		final var updatedSupplierRequest = supplierRequestsFixture.findFor(patronRequest);

		assertThat(updatedSupplierRequest, allOf(
			hasStatusCode(SupplierRequestStatusCode.CANCELLED)
		));

		sierraPatronsAPIFixture.verifyDeleteHoldRequestMade(localSupplyingHoldId);
	}

	@Test
	void shouldAuditAnErrorResponseWhenCancellingALocalHoldProducedAnError() {
		// Arrange
		final var patron = Patron.builder()
			.id(randomUUID())
			.build();

		patronFixture.savePatron(patron);
		final var virtualPatronIdentity = patronFixture.saveIdentityAndReturn(patron, supplierHostLMS, "007",
			false, "-", "LOCAL_SYSTEM_CODE", null);

		final var patronRequest = PatronRequest.builder()
			.id(randomUUID())
			.patron(patron)
			.status(REQUEST_PLACED_AT_BORROWING_AGENCY)
			.localRequestStatus(HOLD_MISSING)
			.build();

		patronRequestsFixture.savePatronRequest(patronRequest);

		final var localSupplyingHoldId = "7357355";

		supplierRequestsFixture.saveSupplierRequest(
			SupplierRequest.builder()
				.id(randomUUID())
				.localStatus(HOLD_CONFIRMED)
				.localId(localSupplyingHoldId)
				.localItemId("647375678")
				.localItemBarcode("26123553")
				.patronRequest(patronRequest)
				.hostLmsCode(SUPPLYING_HOST_LMS_CODE)
				.virtualIdentity(virtualPatronIdentity)
				.build());

		sierraPatronsAPIFixture.mockGetHoldById(localSupplyingHoldId,
			SierraPatronHold.builder().id(localSupplyingHoldId).build());
		sierraPatronsAPIFixture.mockDeleteHoldError(localSupplyingHoldId);

		// Act
		final var updatedPatronRequest = attemptCancelledPatronRequestTransition(patronRequest);

		// Assert
		assertThat(updatedPatronRequest, allOf(
			notNullValue(),
			hasStatus(CANCELLED)
		));

		sierraPatronsAPIFixture.verifyDeleteHoldRequestMade(localSupplyingHoldId);

		final var auditList = mapStream(patronRequestsFixture.findAuditEntries(updatedPatronRequest),
				PatronRequestAudit::getBriefDescription)
			.filter("Delete supplier hold : Failed"::equals)
			.toList();

		assertThat(auditList, hasSize(1));
	}

	private PatronRequest attemptCancelledPatronRequestTransition(PatronRequest patronRequest) {
		return singleValueFrom(requestWorkflowContextHelper.fromPatronRequest(patronRequest)
			.flatMap(ctx -> {
				if (!cancelledPatronRequestTransition.isApplicableFor(ctx)) {
					return Mono.error(new RuntimeException("cancelledPatronRequestTransition is not applicable for request"));
				}

				return cancelledPatronRequestTransition.attempt(ctx);
			})
			.thenReturn(patronRequest));
	}
}
