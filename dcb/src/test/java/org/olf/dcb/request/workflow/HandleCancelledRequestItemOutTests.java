package org.olf.dcb.request.workflow;

import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.mockserver.model.HttpResponse.response;
import static org.olf.dcb.core.interaction.HostLmsItem.ITEM_AVAILABLE;
import static org.olf.dcb.core.interaction.HostLmsItem.ITEM_TRANSIT;
import static org.olf.dcb.core.interaction.HostLmsRequest.HOLD_CANCELLED;
import static org.olf.dcb.core.interaction.HostLmsRequest.HOLD_CONFIRMED;
import static org.olf.dcb.core.interaction.HostLmsRequest.HOLD_MISSING;
import static org.olf.dcb.core.model.PatronRequest.Status.AWAITING_RETURN_TO_SUPPLIER;
import static org.olf.dcb.core.model.PatronRequest.Status.CANCELLED;
import static org.olf.dcb.test.MockServerCommonResponses.okJson;
import static org.olf.dcb.core.model.PatronRequest.Status.LOANED;
import static org.olf.dcb.core.model.PatronRequest.Status.PICKUP_TRANSIT;
import static org.olf.dcb.core.model.PatronRequest.Status.READY_FOR_PICKUP;
import static org.olf.dcb.core.model.PatronRequest.Status.RECEIVED_AT_PICKUP;
import static org.olf.dcb.core.model.PatronRequest.Status.REQUEST_PLACED_AT_BORROWING_AGENCY;
import static org.olf.dcb.core.model.PatronRequest.Status.RETURN_TRANSIT;
import static org.olf.dcb.core.model.WorkflowConstants.PICKUP_ANYWHERE_WORKFLOW;
import static org.olf.dcb.core.model.WorkflowConstants.STANDARD_WORKFLOW;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;
import static org.olf.dcb.test.matchers.PatronRequestMatchers.hasStatus;
import static org.olf.dcb.utils.CollectionUtils.mapStream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockserver.client.MockServerClient;
import org.olf.dcb.core.interaction.folio.MockFolioFixture;
import org.olf.dcb.core.interaction.sierra.SierraApiFixtureProvider;
import org.olf.dcb.core.interaction.sierra.SierraPatronsAPIFixture;
import org.olf.dcb.core.model.DataHostLms;
import org.olf.dcb.core.model.Patron;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.PatronRequest.Status;
import org.olf.dcb.core.model.PatronRequestAudit;
import org.olf.dcb.core.model.SupplierRequest;
import org.olf.dcb.request.fulfilment.RequestWorkflowContext;
import org.olf.dcb.request.fulfilment.RequestWorkflowContextHelper;
import org.olf.dcb.test.HostLmsFixture;
import org.olf.dcb.test.PatronFixture;
import org.olf.dcb.test.PatronRequestsFixture;
import org.olf.dcb.test.SupplierRequestsFixture;

import java.util.Map;

import jakarta.inject.Inject;
import services.k_int.interaction.sierra.SierraTestUtils;
import services.k_int.interaction.sierra.holds.SierraPatronHold;
import services.k_int.test.mockserver.MockServerMicronautTest;

@MockServerMicronautTest
@TestInstance(PER_CLASS)
class HandleCancelledRequestItemOutTests {

	private static final String SUPPLYING_HOST_LMS_CODE = "supplier-host-lms";
	private static final String FOLIO_SUPPLYING_HOST_LMS_CODE = "folio-supplier-host-lms";

	@Inject
	private SierraApiFixtureProvider sierraApiFixtureProvider;
	@Inject
	private HostLmsFixture hostLmsFixture;
	@Inject
	private PatronFixture patronFixture;
	@Inject
	private PatronRequestsFixture patronRequestsFixture;
	@Inject
	private SupplierRequestsFixture supplierRequestsFixture;
	@Inject
	private RequestWorkflowContextHelper requestWorkflowContextHelper;
	@Inject
	private HandleCancelledRequestItemOut handleCancelledRequestItemOut;
	@Inject
	private HandleCancelledRequestReturnTransit handleCancelledRequestReturnTransit;

	private SierraPatronsAPIFixture sierraPatronsAPIFixture;
	private DataHostLms supplierHostLMS;
	private DataHostLms folioSupplierHostLMS;
	private MockFolioFixture mockFolioFixture;
	private String BASE_URL;

	@BeforeAll
	void beforeAll(MockServerClient mockServerClient) {
		final String TOKEN = "test-token";
		BASE_URL = "https://handle-cancelled-item-out-tests.com";
		final String KEY = "key";
		final String SECRET = "secret";

		hostLmsFixture.deleteAll();

		SierraTestUtils.mockFor(mockServerClient, BASE_URL)
			.setValidCredentials(KEY, SECRET, TOKEN, 60);

		supplierHostLMS = hostLmsFixture.createSierraHostLms(SUPPLYING_HOST_LMS_CODE, KEY,
			SECRET, BASE_URL, "title");

		sierraPatronsAPIFixture = sierraApiFixtureProvider.patrons(mockServerClient, null);

		// A FOLIO supplier, used to exercise the "cancel releases the item -> finalise" branch.
		final var FOLIO_API_KEY = "eyJzIjoic2FsdCIsInQiOiJ0ZW5hbnQiLCJ1IjoidXNlciJ9";
		folioSupplierHostLMS = hostLmsFixture.createFolioHostLms(FOLIO_SUPPLYING_HOST_LMS_CODE,
			"https://folio-cancelled-item-out-tests", FOLIO_API_KEY, "", "");
		mockFolioFixture = new MockFolioFixture(mockServerClient, "folio-cancelled-item-out-tests", FOLIO_API_KEY);
	}

	@BeforeEach
	void beforeEach() {
		supplierRequestsFixture.deleteAll();
		patronRequestsFixture.deleteAll();
		patronFixture.deleteAllPatrons();
	}

	@ParameterizedTest
	@CsvSource({
		"PICKUP_TRANSIT, MISSING",
		"PICKUP_TRANSIT, CANCELLED",
		"RECEIVED_AT_PICKUP, MISSING",
		"RECEIVED_AT_PICKUP, CANCELLED",
		"READY_FOR_PICKUP, MISSING",
		"READY_FOR_PICKUP, CANCELLED"
	})
	void shouldBeApplicableWhenItemIsOutAndBorrowerHoldIsGone(Status status, String localRequestStatus) {
		// No item-status gate - the request is parked as soon as the hold is gone, wherever the item is.
		final var ctx = contextFor(PatronRequest.builder()
			.id(randomUUID())
			.status(status)
			.localRequestStatus(localRequestStatus)
			.localItemStatus(ITEM_TRANSIT)
			.activeWorkflow(STANDARD_WORKFLOW)
			.build());

		assertThat(handleCancelledRequestItemOut.isApplicableFor(ctx), is(true));
	}

	@Test
	void shouldBeApplicableForPickupAnywhereUsingPickupHoldStatus() {
		// Standard localRequestStatus is confirmed, but the pickup hold (which the patron actually
		// holds against for PUA) is missing - so the transition must read the pickup hold status.
		final var ctx = contextFor(PatronRequest.builder()
			.id(randomUUID())
			.status(RECEIVED_AT_PICKUP)
			.localRequestStatus(HOLD_CONFIRMED)
			.pickupRequestStatus(HOLD_MISSING)
			.activeWorkflow(PICKUP_ANYWHERE_WORKFLOW)
			.build());

		assertThat(handleCancelledRequestItemOut.isApplicableFor(ctx), is(true));
	}

	@Test
	void shouldNotBeApplicableWhenBorrowerHoldIsStillActive() {
		final var ctx = contextFor(PatronRequest.builder()
			.id(randomUUID())
			.status(PICKUP_TRANSIT)
			.localRequestStatus(HOLD_CONFIRMED)
			.activeWorkflow(STANDARD_WORKFLOW)
			.build());

		assertThat(handleCancelledRequestItemOut.isApplicableFor(ctx), is(false));
	}

	@Test
	void shouldNotBeApplicableWhenItemIsNotOut() {
		final var ctx = contextFor(PatronRequest.builder()
			.id(randomUUID())
			.status(REQUEST_PLACED_AT_BORROWING_AGENCY)
			.localRequestStatus(HOLD_MISSING)
			.activeWorkflow(STANDARD_WORKFLOW)
			.build());

		assertThat(handleCancelledRequestItemOut.isApplicableFor(ctx), is(false));
	}

	@Test
	void shouldNotBeApplicableWhenLoaned() {
		final var ctx = contextFor(PatronRequest.builder()
			.id(randomUUID())
			.status(LOANED)
			.localRequestStatus(HOLD_MISSING)
			.activeWorkflow(STANDARD_WORKFLOW)
			.build());

		assertThat(handleCancelledRequestItemOut.isApplicableFor(ctx), is(false));
	}

	@Test
	void shouldDeleteSupplierHoldWhenParkingSoReturningItemIsNotReCaptured() {
		// The supplier hold is only consumed by the supplier-side checkout, which happens in
		// HandleBorrowerItemLoaned. The patron cancelled before loaning, so nothing consumed it - left
		// in place it re-captures the item on check-in at the supplier (Polaris: "transfer for hold")
		// and the item never becomes AVAILABLE. So we must DELETE it (never CANCEL, which would kill
		// FOLIO's mod-dcb tracking transaction).
		final var patron = Patron.builder().id(randomUUID()).build();
		patronFixture.savePatron(patron);
		final var virtualPatronIdentity = patronFixture.saveIdentityAndReturn(patron, supplierHostLMS, "007",
			false, "-", "LOCAL_SYSTEM_CODE", null);

		final var patronRequest = PatronRequest.builder()
			.id(randomUUID())
			.patron(patron)
			.status(PICKUP_TRANSIT)
			.localRequestStatus(HOLD_MISSING)
			.localItemStatus(ITEM_TRANSIT)
			.localItemId("647375678")
			.localBibId("bib-123")
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
				.hostLmsCode(SUPPLYING_HOST_LMS_CODE)
				.virtualIdentity(virtualPatronIdentity)
				.build());

		// cleanUp checks the hold exists before deleting it
		sierraPatronsAPIFixture.mockGetHoldById(localSupplyingHoldId, SierraPatronHold.builder()
			.id("%s/iii/sierra-api/v6/patrons/holds/%s".formatted(BASE_URL, localSupplyingHoldId))
			.build());
		sierraPatronsAPIFixture.mockDeleteHold(localSupplyingHoldId);

		// Act
		final var updated = singleValueFrom(requestWorkflowContextHelper.fromPatronRequest(patronRequest)
			.flatMap(handleCancelledRequestItemOut::attempt)
			.map(RequestWorkflowContext::getPatronRequest));

		// Assert - parked, and the supplier hold was removed so the item can go AVAILABLE on return
		assertThat(updated, allOf(notNullValue(), hasStatus(AWAITING_RETURN_TO_SUPPLIER)));

		sierraPatronsAPIFixture.verifyDeleteHoldRequestMade(localSupplyingHoldId);

		final var auditEntries = mapStream(patronRequestsFixture.findAuditEntries(patronRequest),
				PatronRequestAudit::getBriefDescription)
			.filter(HandleCancelledRequestItemOut.ITEM_OUT_HELD_AWAITING_RETURN::equals)
			.toList();

		assertThat(auditEntries, hasSize(1));
	}

	@Test
	void folioSupplierShouldFinaliseInsteadOfParkingBecauseCancelReleasesTheItem() {
		// For a FOLIO supplier, cancelling the mod-dcb transaction returns the item to available at the
		// supplier - there is no physical return to wait on (and none DCB could track). So instead of
		// parking we set CANCELLED and let the request finalise. Contrast the Sierra test above, which parks.
		final var patron = Patron.builder().id(randomUUID()).build();
		patronFixture.savePatron(patron);
		final var virtualPatronIdentity = patronFixture.saveIdentityAndReturn(patron, folioSupplierHostLMS,
			"folio-patron-barcode", false, "-", "LOCAL_SYSTEM_CODE", null);

		final var transactionId = randomUUID().toString();

		final var patronRequest = PatronRequest.builder()
			.id(randomUUID())
			.patron(patron)
			.status(PICKUP_TRANSIT)
			.localRequestStatus(HOLD_MISSING)
			.localItemStatus(ITEM_TRANSIT)
			.localItemId(randomUUID().toString())
			.localBibId("bib-folio")
			.activeWorkflow(STANDARD_WORKFLOW)
			.build();

		patronRequestsFixture.savePatronRequest(patronRequest);

		supplierRequestsFixture.saveSupplierRequest(
			SupplierRequest.builder()
				.id(randomUUID())
				.localStatus(HOLD_CONFIRMED)
				.localId(transactionId)
				.localItemId(patronRequest.getLocalItemId())
				.localItemBarcode("folio-item-barcode")
				.patronRequest(patronRequest)
				.hostLmsCode(FOLIO_SUPPLYING_HOST_LMS_CODE)
				.virtualIdentity(virtualPatronIdentity)
				.build());

		// cleanUp -> checkHoldExists (transaction present) -> deleteHold: CLOSE rejected -> CANCELLED.
		mockFolioFixture.mockGetTransactionStatus(transactionId, "OPEN");
		mockFolioFixture.mockUpdateTransactionStatus(transactionId, "CLOSED", response().withStatusCode(422));
		mockFolioFixture.mockUpdateTransactionStatus(transactionId, "CANCELLED",
			okJson(Map.of("status", "CANCELLED")));

		// Act
		final var updated = singleValueFrom(requestWorkflowContextHelper.fromPatronRequest(patronRequest)
			.flatMap(handleCancelledRequestItemOut::attempt)
			.map(RequestWorkflowContext::getPatronRequest));

		// Assert - finalised path (CANCELLED), not parked
		assertThat(updated, allOf(notNullValue(), hasStatus(CANCELLED)));

		final var auditEntries = mapStream(patronRequestsFixture.findAuditEntries(patronRequest),
				PatronRequestAudit::getBriefDescription)
			.filter(HandleCancelledRequestItemOut.ITEM_OUT_CANCELLED_AND_FINALISING::equals)
			.toList();

		assertThat(auditEntries, hasSize(1));
	}

	@Test
	void parkedRequestShouldNotReleaseWhileBorrowerItemIsMerelyInTransit() {
		// Regression: a request cancelled during PICKUP_TRANSIT has its borrower virtual item in TRANSIT -
		// the OUTBOUND leg, item heading TO the borrower. Outbound and return transit share the exact same
		// TRANSIT status, so the borrower item must NOT trigger release; otherwise the request jumps
		// straight to RETURN_TRANSIT at park time, before the item is anywhere near coming back. Release
		// keys solely off the supplier actually having the item.
		final var stillOnShelf = contextFor(PatronRequest.builder()
			.id(randomUUID())
			.status(AWAITING_RETURN_TO_SUPPLIER)
			.localItemStatus("HOLDSHELF")
			.activeWorkflow(STANDARD_WORKFLOW)
			.build());

		assertThat("Not released while the item is still sitting on the pickup shelf",
			handleCancelledRequestReturnTransit.isApplicableFor(stillOnShelf), is(false));

		final var outboundTransit = new RequestWorkflowContext()
			.setPatronRequest(PatronRequest.builder()
				.id(randomUUID())
				.status(AWAITING_RETURN_TO_SUPPLIER)
				.localItemStatus(ITEM_TRANSIT)
				.activeWorkflow(STANDARD_WORKFLOW)
				.build())
			.setSupplierRequest(SupplierRequest.builder().id(randomUUID()).localItemStatus(ITEM_TRANSIT).build());

		assertThat("Not released just because the borrower item is in transit - outbound and return look identical",
			handleCancelledRequestReturnTransit.isApplicableFor(outboundTransit), is(false));
	}

	@Test
	void parkedFolioBorrowerRequestShouldReleaseOnSupplierItemBack() {
		// Regression for the FOLIO-borrower stall: the cancelled mod-dcb borrower transaction is
		// terminal, so the borrower virtual item never reports TRANSIT (DCB sees a passthrough
		// "CANCELLED"). If the release only watched the borrower item the request would be stranded
		// in AWAITING even though the supplier (e.g. Polaris) already has the item back. The release
		// must key off the supplier item being AVAILABLE.
		final var patronRequest = PatronRequest.builder()
			.id(randomUUID())
			.status(AWAITING_RETURN_TO_SUPPLIER)
			.localRequestStatus(HOLD_CANCELLED)
			.localItemStatus("CANCELLED")
			.activeWorkflow(STANDARD_WORKFLOW)
			.build();

		final var supplierHasItemBack = new RequestWorkflowContext()
			.setPatronRequest(patronRequest)
			.setSupplierRequest(SupplierRequest.builder().id(randomUUID()).localItemStatus(ITEM_AVAILABLE).build());

		assertThat("Released once the supplier has the item back, despite the FOLIO borrower item never reporting transit",
			handleCancelledRequestReturnTransit.isApplicableFor(supplierHasItemBack), is(true));

		final var updated = singleValueFrom(handleCancelledRequestReturnTransit.attempt(supplierHasItemBack)
			.map(RequestWorkflowContext::getPatronRequest));

		assertThat(updated, allOf(notNullValue(), hasStatus(RETURN_TRANSIT)));
	}

	@Test
	void parkedRequestShouldNotReleaseWhileItemIsNeitherInTransitNorBackAtSupplier() {
		final var patronRequest = PatronRequest.builder()
			.id(randomUUID())
			.status(AWAITING_RETURN_TO_SUPPLIER)
			.localItemStatus("HOLDSHELF")
			.activeWorkflow(STANDARD_WORKFLOW)
			.build();

		final var stillOut = new RequestWorkflowContext()
			.setPatronRequest(patronRequest)
			.setSupplierRequest(SupplierRequest.builder().id(randomUUID()).localItemStatus(ITEM_TRANSIT).build());

		assertThat("Not released while the supplier item is still in transit and the borrower item is on the shelf",
			handleCancelledRequestReturnTransit.isApplicableFor(stillOut), is(false));
	}

	private RequestWorkflowContext contextFor(PatronRequest patronRequest) {
		return new RequestWorkflowContext().setPatronRequest(patronRequest);
	}
}
