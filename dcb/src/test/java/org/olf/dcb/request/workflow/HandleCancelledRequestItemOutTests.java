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
import static org.olf.dcb.core.interaction.HostLmsItem.ITEM_LOANED;
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
import static org.olf.dcb.core.model.WorkflowConstants.PICKUP_ANYWHERE_WORKFLOW;
import static org.olf.dcb.core.model.WorkflowConstants.STANDARD_WORKFLOW;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;
import static org.olf.dcb.test.matchers.PatronRequestMatchers.hasOutcome;
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
	private HandleCancelledRequestItemReturned handleCancelledRequestItemReturned;
	@Inject
	private PatronRequestWorkflowService patronRequestWorkflowService;

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
		// Wherever the item is, as long as it is not with the patron - see
		// patronCollectingTheItemMustNotBeTreatedAsACancellation for the one status that excludes it.
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
	void shouldBeApplicableForPickupAnywhereWhenThePatronCancelsAtTheirHomeLibrary() {
		// REGRESSION. In PUA the patron's OWN hold is the one at their home (borrowing) library - that is
		// what they see and what they cancel. The pickup hold is one DCB placed against a virtual patron
		// so the item can sit on the pickup shelf; CancelledPatronRequestTransition treats the borrower
		// hold as the trigger and then tears the pickup hold down as a consequence.
		//
		// Watching only the pickup hold here silently drops every PUA patron cancellation: the request is
		// no longer claimed by CancelledPatronRequestTransition either (narrowed to the pre-shipment
		// states), so nothing moves it and it never reaches FINALISED.
		final var ctx = contextFor(PatronRequest.builder()
			.id(randomUUID())
			.status(RECEIVED_AT_PICKUP)
			.localRequestStatus(HOLD_CANCELLED)
			.pickupRequestStatus(HOLD_CONFIRMED)
			.activeWorkflow(PICKUP_ANYWHERE_WORKFLOW)
			.build());

		assertThat("A PUA patron cancelling at their home library is still a cancellation",
			handleCancelledRequestItemOut.isApplicableFor(ctx), is(true));
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
	void patronCollectingTheItemMustNotBeTreatedAsACancellation() {
		// REGRESSION. Sierra and Polaris consume the local hold when the patron checks the item out, so
		// localRequestStatus goes MISSING at the same moment localItemStatus goes LOANED. Tracking polls
		// the request before the item (TrackingServiceV3.trackBorrowingSystem) and only progresses the
		// workflow once, at the end of the cycle, so the engine sees BOTH facts in one context.
		//
		// A missing hold on its own is NOT a cancellation. If the item is with the patron it is a
		// collection, and HandleBorrowerItemLoaned must win. Note the engine breaks the tie by reverse
		// alphabetical name (PatronRequestWorkflowService.getPossibleStateTransitionsFor), where
		// "HandleCancelledRequestItemOut" outranks "HandleBorrowerItemLoaned" - so this transition MUST
		// exclude itself on the item status. Do not fix this by renaming.
		final var ctx = contextWithSupplierFor(PatronRequest.builder()
			.id(randomUUID())
			.status(READY_FOR_PICKUP)
			.localRequestStatus(HOLD_MISSING)
			.localItemStatus(ITEM_LOANED)
			.activeWorkflow(STANDARD_WORKFLOW)
			.build());

		assertThat("A collected item is not a cancellation",
			handleCancelledRequestItemOut.isApplicableFor(ctx), is(false));

		assertThat("The engine must route a collected item to the loan transition",
			firstApplicableTransitionFor(ctx), is("HandleBorrowerItemLoaned"));
	}

	@Test
	void pickupAnywhereCollectionMustNotBeTreatedAsACancellation() {
		// Same regression on the PUA leg: the patron holds against the pickup system, so it is the
		// pickup hold that is consumed on checkout and the pickup item that reports LOANED.
		final var ctx = contextFor(PatronRequest.builder()
			.id(randomUUID())
			.status(READY_FOR_PICKUP)
			.localRequestStatus(HOLD_CONFIRMED)
			.pickupRequestStatus(HOLD_MISSING)
			.pickupItemStatus(ITEM_LOANED)
			.activeWorkflow(PICKUP_ANYWHERE_WORKFLOW)
			.build());

		assertThat("A collected item is not a cancellation",
			handleCancelledRequestItemOut.isApplicableFor(ctx), is(false));
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

		// Assert - finalised path (CANCELLED), not parked.
		// The outcome must be recorded too: it is the field reporting keys off, and this path bypasses
		// CancelledPatronRequestTransition, which is otherwise the only thing that sets Outcome.CANCELLED.
		assertThat(updated, allOf(
			notNullValue(),
			hasStatus(CANCELLED),
			hasOutcome(PatronRequest.Outcome.CANCELLED)));

		final var auditEntries = mapStream(patronRequestsFixture.findAuditEntries(patronRequest),
				PatronRequestAudit::getBriefDescription)
			.filter(HandleCancelledRequestItemOut.ITEM_OUT_RETURN_NOT_REPORTABLE::equals)
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
			handleCancelledRequestItemReturned.isApplicableFor(stillOnShelf), is(false));

		final var outboundTransit = new RequestWorkflowContext()
			.setPatronRequest(PatronRequest.builder()
				.id(randomUUID())
				.status(AWAITING_RETURN_TO_SUPPLIER)
				.localItemStatus(ITEM_TRANSIT)
				.activeWorkflow(STANDARD_WORKFLOW)
				.build())
			.setSupplierRequest(SupplierRequest.builder().id(randomUUID()).localItemStatus(ITEM_TRANSIT).build());

		assertThat("Not released just because the borrower item is in transit - outbound and return look identical",
			handleCancelledRequestItemReturned.isApplicableFor(outboundTransit), is(false));
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
			handleCancelledRequestItemReturned.isApplicableFor(supplierHasItemBack), is(true));

		final var updated = singleValueFrom(handleCancelledRequestItemReturned.attempt(supplierHasItemBack)
			.map(RequestWorkflowContext::getPatronRequest));

		// CANCELLED, not RETURN_TRANSIT: nothing was supplied. Rejoining the return leg would end in
		// HandleSupplierItemAvailable and record Outcome.SUPPLIED for an item nobody ever received.
		assertThat(updated, allOf(
			notNullValue(),
			hasStatus(CANCELLED),
			hasOutcome(PatronRequest.Outcome.CANCELLED)));
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
			handleCancelledRequestItemReturned.isApplicableFor(stillOut), is(false));
	}

	private RequestWorkflowContext contextFor(PatronRequest patronRequest) {
		return new RequestWorkflowContext().setPatronRequest(patronRequest);
	}

	private RequestWorkflowContext contextWithSupplierFor(PatronRequest patronRequest) {
		return contextFor(patronRequest)
			.setSupplierRequest(SupplierRequest.builder()
				.id(randomUUID())
				.localId("supplier-hold-id")
				.localItemId("supplier-item-id")
				.localStatus(HOLD_CONFIRMED)
				.localItemStatus(ITEM_TRANSIT)
				.hostLmsCode(SUPPLYING_HOST_LMS_CODE)
				.build());
	}

	/**
	 * The transition the workflow engine would actually pick for this context. Guards are tested in
	 * isolation elsewhere; this exercises the tie-break, which is where collisions between overlapping
	 * transitions actually bite.
	 */
	private String firstApplicableTransitionFor(RequestWorkflowContext ctx) {
		return patronRequestWorkflowService.getPossibleStateTransitionsFor(ctx)
			.map(PatronRequestStateTransition::getName)
			.findFirst()
			.orElse("None");
	}
}


