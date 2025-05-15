package org.olf.dcb.request.workflow;

import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockserver.client.MockServerClient;
import org.olf.dcb.core.interaction.sierra.SierraApiFixtureProvider;
import org.olf.dcb.core.interaction.sierra.SierraBibsAPIFixture;
import org.olf.dcb.core.interaction.sierra.SierraItemsAPIFixture;
import org.olf.dcb.core.interaction.sierra.SierraPatronsAPIFixture;
import org.olf.dcb.core.model.*;
import org.olf.dcb.request.fulfilment.RequestWorkflowContext;
import org.olf.dcb.request.fulfilment.RequestWorkflowContextHelper;
import org.olf.dcb.request.fulfilment.SupplierRequestStatusCode;
import org.olf.dcb.test.*;
import reactor.core.publisher.Mono;
import services.k_int.interaction.sierra.SierraTestUtils;
import services.k_int.test.mockserver.MockServerMicronautTest;

import java.util.Optional;
import java.util.UUID;

import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.olf.dcb.core.interaction.HostLmsRequest.HOLD_MISSING;
import static org.olf.dcb.core.model.PatronRequest.Status.*;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;
import static org.olf.dcb.test.matchers.PatronRequestMatchers.hasStatus;

@MockServerMicronautTest
@TestInstance(PER_CLASS)
class FinaliseRequestTransitionTests {
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
	private SierraBibsAPIFixture sierraBibsAPIFixture;
	private SierraItemsAPIFixture sierraItemsAPIFixture;
	@Inject
	private RequestWorkflowContextHelper requestWorkflowContextHelper;
	@Inject
	private PatronRequestWorkflowService patronRequestWorkflowService;
	@Inject
	private FinaliseRequestTransition finaliseRequestTransition;
	@Inject
	private LocationFixture locationFixture;
	private DataHostLms supplierHostLMS;
	private DataHostLms pickupHostLMS;
	private static final String SUPPLYING_HOST_LMS_CODE = "finalise-request-tests";
	private static final String BORROWING_HOST_LMS_CODE = "finalise-request-tests";
	private static final String PICKUP_HOST_LMS_CODE = "pickup-finalise-request-tests";
	private static final String PICKUP_LOCATION_CODE = "ABC123";
	private static final String VALID_PICKUP_LOCATION_ID = "0f102b5a-e300-41c8-9aca-afd170e17921";
	private static final String PICKUP_AGENCY_CODE = "pickup-agency";


	@BeforeAll
	void beforeAll(MockServerClient mockServerClient) {
		final String TOKEN = "test-token";
		final String BASE_URL = "https://borrowing-agency-service-tests.com";
		final String KEY = "borrowing-agency-service-key";
		final String SECRET = "borrowing-agency-service-secret";

		hostLmsFixture.deleteAll();
		locationFixture.deleteAll();
		agencyFixture.deleteAll();

		SierraTestUtils.mockFor(mockServerClient, BASE_URL)
			.setValidCredentials(KEY, SECRET, TOKEN, 60);

		supplierHostLMS = hostLmsFixture.createSierraHostLms(SUPPLYING_HOST_LMS_CODE, KEY,
			SECRET, BASE_URL, "title");
		pickupHostLMS = hostLmsFixture.createSierraHostLms(PICKUP_HOST_LMS_CODE, KEY,
			SECRET, BASE_URL, "title");

		agencyFixture.defineAgency(PICKUP_AGENCY_CODE, "Pickup Agency", pickupHostLMS);

		sierraPatronsAPIFixture = sierraApiFixtureProvider.patronsApiFor(mockServerClient);
		sierraBibsAPIFixture = sierraApiFixtureProvider.bibsApiFor(mockServerClient);
		sierraItemsAPIFixture = sierraApiFixtureProvider.itemsApiFor(mockServerClient);
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
		final var patronRequest = PatronRequest.builder()
			.id(randomUUID())
			.status(SUBMITTED_TO_DCB)
			.build();

		final var ctx = new RequestWorkflowContext();
		ctx.setPatronRequest(patronRequest);

		// Act
		final var applicable = finaliseRequestTransition.isApplicableFor(ctx);

		// Assert
		assertThat("Should not be applicable for a submitted DCB status",
			applicable, is(false));
	}

	@Test
	void shouldProgressPatronRequestToFinalisedWhenPatronHasBeenCancelled() {
		// Arrange
		final var patron = patronFixture.savePatron(Patron.builder()
			.id(randomUUID())
			.build());

		final var virtualPatronIdentity = patronFixture.saveIdentityAndReturn(patron,
			supplierHostLMS,
			"700",
			false,
			"-",
			"LOCAL_SYSTEM_CODE",
			null);

		final var patronRequest = patronRequestsFixture.savePatronRequest(PatronRequest.builder()
			.id(randomUUID())
			.patron(patron)
			.status(COMPLETED)
			.localRequestId("43634634")
			.localRequestStatus(HOLD_MISSING)
			.localBibId("328947")
			.localItemId("75432")
			.patronHostlmsCode(BORROWING_HOST_LMS_CODE)
			.build());

		final var supplierRequest = supplierRequestsFixture.saveSupplierRequest(
			SupplierRequest.builder()
				.id(randomUUID())
				.localStatus(HOLD_MISSING)
				.localId("7357356")
				.localItemId("647375678")
				.localItemBarcode("26123553")
				.patronRequest(patronRequest)
				.statusCode(SupplierRequestStatusCode.CANCELLED)
				.hostLmsCode(SUPPLYING_HOST_LMS_CODE)
				.virtualIdentity(virtualPatronIdentity)
				.build());

		createStandardWorkflowMocksForFinalisationTransition(patronRequest, supplierRequest.getLocalId(), virtualPatronIdentity);

		// Act
		final var updatedPatronRequest = finalisePatronRequest(patronRequest);

		// Assert
		assertPatronRequestWasFinalised(updatedPatronRequest);
		assertStatusOfVirtualRecordsWithAudit(updatedPatronRequest);
	}

	@Test
	void shouldProgressPatronRequestToFinalisedWhenAPatronRequestHasCompleted() {
		// Arrange
		final var patron = patronFixture.savePatron(Patron.builder()
			.id(randomUUID())
			.build());

		final var virtualPatronIdentity = patronFixture.saveIdentityAndReturn(patron,
			supplierHostLMS,
			"700",
			false,
			"-",
			"LOCAL_SYSTEM_CODE",
			null);

		final var patronRequest = patronRequestsFixture.savePatronRequest(PatronRequest.builder()
			.id(randomUUID())
			.patron(patron)
			.status(COMPLETED)
			.localRequestId("43634634")
			.localRequestStatus(HOLD_MISSING)
			.localBibId("328947")
			.localItemId("75432")
			.patronHostlmsCode(BORROWING_HOST_LMS_CODE)
			.build());

		final var supplierRequest = supplierRequestsFixture.saveSupplierRequest(
			SupplierRequest.builder()
				.id(randomUUID())
				.localStatus(HOLD_MISSING)
				.localId("7357356")
				.localItemId("647375678")
				.localItemBarcode("26123553")
				.patronRequest(patronRequest)
				.statusCode(SupplierRequestStatusCode.CANCELLED)
				.hostLmsCode(SUPPLYING_HOST_LMS_CODE)
				.virtualIdentity(virtualPatronIdentity)
				.build());

		createStandardWorkflowMocksForFinalisationTransition(patronRequest, supplierRequest.getLocalId(), virtualPatronIdentity);

		// Act
		final var updatedPatronRequest = finalisePatronRequest(patronRequest);

		// Assert
		assertPatronRequestWasFinalised(updatedPatronRequest);
		assertStatusOfVirtualRecordsWithAudit(updatedPatronRequest);
	}

	@Test
	void shouldProgressPatronRequestToFinalisedWhenAPickupAnywhereRequestHasCompleted() {
		// Arrange
		final var patron = patronFixture.savePatron(Patron.builder()
			.id(randomUUID())
			.build());

		final var virtualPatronIdentity = patronFixture.saveIdentityAndReturn(patron,
			supplierHostLMS,
			"700",
			false,
			"-",
			"LOCAL_SYSTEM_CODE",
			null);

		final var pickupPatronIdentity = patronFixture.saveIdentityAndReturn(patron,
			pickupHostLMS,
			"800",
			false,
			"-",
			"LOCAL_SYSTEM_CODE",
			null);

		final var pickupLocation = createPickupLocation();

		final var patronRequest = patronRequestsFixture.savePatronRequest(PatronRequest.builder()
			.id(randomUUID())
			.patron(patron)
			.status(COMPLETED)
			.localRequestId("43634634")
			.localRequestStatus(HOLD_MISSING)
			.localBibId("328947")
			.localItemId("75432")
			.patronHostlmsCode(BORROWING_HOST_LMS_CODE)
			.activeWorkflow("RET-PUA")
			.pickupBibId("435432")
			.pickupItemId("646345")
			.pickupPatronId(pickupPatronIdentity.getLocalId())
			.pickupLocationCode(VALID_PICKUP_LOCATION_ID)
			.build());

		final var supplierRequest = supplierRequestsFixture.saveSupplierRequest(
			SupplierRequest.builder()
				.id(randomUUID())
				.localStatus(HOLD_MISSING)
				.localId("7357356")
				.localItemId("647375678")
				.localItemBarcode("26123553")
				.patronRequest(patronRequest)
				.statusCode(SupplierRequestStatusCode.CANCELLED)
				.hostLmsCode(SUPPLYING_HOST_LMS_CODE)
				.virtualIdentity(virtualPatronIdentity)
				.build());

		createStandardWorkflowMocksForFinalisationTransition(patronRequest, supplierRequest.getLocalId(), virtualPatronIdentity);
		createPUAMocksForFinalisationTransition(patronRequest, pickupPatronIdentity);

		// Act
		final var updatedPatronRequest = finalisePatronRequest(patronRequest);

		// Assert
		assertPatronRequestWasFinalised(updatedPatronRequest);
		assertStatusOfVirtualRecordsWithAudit(updatedPatronRequest);
	}

	private void createStandardWorkflowMocksForFinalisationTransition(
		PatronRequest patronRequest, String localSupplyingHoldId, PatronIdentity virtualPatronIdentity) {

		// local clean up
		sierraBibsAPIFixture.mockDeleteBib(patronRequest.getLocalBibId());
		sierraItemsAPIFixture.mockDeleteItem(patronRequest.getLocalItemId());

		// state verification
		sierraItemsAPIFixture.mockGetItemByIdReturnNoRecordsFound(patronRequest.getLocalItemId());
		sierraPatronsAPIFixture.mockGetHoldByIdNotFound(localSupplyingHoldId);
		sierraPatronsAPIFixture.noRecordsFoundWhenGettingPatronByLocalId(virtualPatronIdentity.getLocalId());
	}

	private void createPUAMocksForFinalisationTransition(
		PatronRequest patronRequest, PatronIdentity pickupPatronIdentity) {

		// pickup system cleanup
		sierraBibsAPIFixture.mockDeleteBib(patronRequest.getPickupBibId());
		sierraItemsAPIFixture.mockDeleteItem(patronRequest.getPickupItemId());

		// state verification
		sierraItemsAPIFixture.mockGetItemByIdReturnNoRecordsFound(patronRequest.getLocalItemId());
		sierraPatronsAPIFixture.mockGetHoldByIdNotFound(patronRequest.getPickupRequestId());
		sierraPatronsAPIFixture.noRecordsFoundWhenGettingPatronByLocalId(pickupPatronIdentity.getLocalId());
	}

	private static void assertPatronRequestWasFinalised(PatronRequest updatedPatronRequest) {
		assertThat(updatedPatronRequest, allOf(
			notNullValue(),
			hasStatus(FINALISED)
		));
	}

	private void assertStatusOfVirtualRecordsWithAudit(PatronRequest updatedPatronRequest) {
		final var audits = patronRequestsFixture.findAuditEntries(updatedPatronRequest).toString();

		assertThat(audits, containsString("briefDescription=Clean up result"));
		assertThat(audits, containsString("VirtualRequest=HostLmsRequest(" +
			"localId=7357356, " +
			"status=MISSING, " +
			"rawStatus=null, " +
			"requestedItemId=null, " +
			"requestedItemBarcode=null"));
		assertThat(audits, containsString("localId=75432"));
		assertThat(audits, containsString("VirtualPatron"));

		final var activeWorkflow = Optional.ofNullable(updatedPatronRequest)
			.map(PatronRequest::getActiveWorkflow)
			.orElse(null);

		if ("RET-PUA".equals(activeWorkflow)) {
			assertThat(audits, containsString("PickupItem"));
			assertThat(audits, containsString("PickupPatron"));
		}
		else {
			assertThat(audits, not(containsString("PickupItem")));
			assertThat(audits, not(containsString("PickupPatron")));
		}
	}

	private PatronRequest finalisePatronRequest(PatronRequest patronRequest) {
		return singleValueFrom(requestWorkflowContextHelper.fromPatronRequest(patronRequest)
			.flatMap(ctx -> {
				if (!finaliseRequestTransition.isApplicableFor(ctx)) {
					return Mono.error(new RuntimeException("finaliseRequestTransition is not applicable for request"));
				}

				return Mono.just(ctx.getPatronRequest())
					.flatMap(patronRequestWorkflowService.attemptTransitionWithErrorTransformer(finaliseRequestTransition, ctx));
			})
			.thenReturn(patronRequest));
	}

	private Location createPickupLocation() {
		return locationFixture.createPickupLocation(UUID.fromString(VALID_PICKUP_LOCATION_ID),
			PICKUP_LOCATION_CODE, PICKUP_LOCATION_CODE, agencyFixture.findByCode(PICKUP_AGENCY_CODE));
	}
}
