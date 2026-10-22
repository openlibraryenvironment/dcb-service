package org.olf.dcb.api;

import static java.util.UUID.randomUUID;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.olf.dcb.core.model.PatronRequest.Status.CANCELLED;
import static org.olf.dcb.core.model.PatronRequest.Status.ERROR;
import static org.olf.dcb.core.model.PatronRequest.Status.FINALISED;
import static org.olf.dcb.core.model.PatronRequest.Status.LOANED;
import static org.olf.dcb.core.model.PatronRequest.Status.PICKUP_TRANSIT;
import static org.olf.dcb.core.model.PatronRequest.Status.READY_FOR_PICKUP;
import static org.olf.dcb.core.model.PatronRequest.Status.REQUEST_PLACED_AT_BORROWING_AGENCY;
import static org.olf.dcb.core.model.PatronRequest.Status.REQUEST_PLACED_AT_PICKUP_AGENCY;
import static org.olf.dcb.core.model.PatronRequest.Status.RETURN_TRANSIT;
import static org.olf.dcb.core.model.WorkflowConstants.PICKUP_ANYWHERE_WORKFLOW;
import static org.olf.dcb.core.model.WorkflowConstants.STANDARD_WORKFLOW;
import static org.olf.dcb.devtools.interaction.dummy.responder.DummyConstants.COMPLETED_STATE;
import static org.olf.dcb.devtools.interaction.dummy.responder.DummyConstants.CUSTOM_RESPONSE_TYPE;
import static org.olf.dcb.devtools.interaction.dummy.responder.DummyConstants.LOANED_STATE;
import static org.olf.dcb.devtools.interaction.dummy.responder.DummyConstants.PICKUP_ROLE;
import static org.olf.dcb.devtools.interaction.dummy.responder.DummyConstants.READY_FOR_PICKUP_STATE;
import static org.olf.dcb.devtools.interaction.dummy.responder.DummyConstants.RECEIVED_AT_PICKUP_STATE;
import static org.olf.dcb.devtools.interaction.dummy.responder.DummyConstants.RETURN_TRANSIT_STATE;
import static org.olf.dcb.devtools.interaction.dummy.responder.DummyConstants.RET_PUA_RESPONSE_TYPE;
import static org.olf.dcb.devtools.interaction.dummy.responder.DummyConstants.RET_STD_RESPONSE_TYPE;
import static org.olf.dcb.devtools.interaction.dummy.responder.DummyConstants.SUPPLIER_ROLE;
import static org.olf.dcb.test.matchers.PatronRequestMatchers.hasActiveWorkflow;

import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.HttpRequest;
import org.mockserver.mock.action.ExpectationResponseCallback;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockserver.client.MockServerClient;
import org.olf.dcb.core.interaction.koha.MockKohaFixture;
import org.olf.dcb.core.interaction.koha.dto.KohaBiblio;
import org.olf.dcb.core.interaction.koha.dto.KohaCheckout;
import org.olf.dcb.core.interaction.koha.dto.KohaHoldResponse;
import org.olf.dcb.core.interaction.koha.dto.KohaItem;
import org.olf.dcb.core.interaction.koha.dto.KohaPatron;
import org.olf.dcb.core.interaction.koha.dto.KohaRenewability;
import org.olf.dcb.core.model.DataAgency;
import org.olf.dcb.core.model.DataHostLms;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.test.AgencyFixture;
import org.olf.dcb.test.BibRecordFixture;
import org.olf.dcb.test.ClusterRecordFixture;
import org.olf.dcb.test.ConsortiumFixture;
import org.olf.dcb.test.EventLogFixture;
import org.olf.dcb.test.HostLmsFixture;
import org.olf.dcb.test.LocationFixture;
import org.olf.dcb.test.PatronFixture;
import org.olf.dcb.test.PatronRequestsFixture;
import org.olf.dcb.test.ReferenceValueMappingFixture;
import org.olf.dcb.test.SupplierRequestsFixture;

import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import services.k_int.test.mockserver.MockServerMicronautTest;

/**
 * A Koha library's patron borrows from another system, start to finish.
 * <p>
 * The supplier is the dummy Host LMS; the borrower is a Koha behind the mock REST API.
 * What moves the request is what Koha staff do: check the arriving virtual item in at the
 * pickup branch (Koha traps the hold), check it out to the patron, check it in on return.
 * Each of those is a change in what Koha answers for the virtual item, and nothing else.
 */
@MockServerMicronautTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Slf4j
class KohaBorrowerScenarioTests {
	private static final String SUPPLYING_HOST_LMS_CODE = "koha-scenario-supplier";
	private static final String KOHA_HOST_LMS_CODE = "koha-scenario-borrower";
	private static final String KOHA_HOST = "fake-koha-scenario";
	private static final String SUPPLYING_LOCATION_CODE = "ab6";
	private static final String SUPPLYING_AGENCY_CODE = "koha-scenario-supplying-agency";
	private static final String BORROWING_AGENCY_CODE = "koha-scenario-borrowing-agency";
	private static final String HOME_BRANCH = "BRANCH-N";
	private static final String PATRON_ID = "12";
	private static final String VIRTUAL_BIBLIO_ID = "4001";
	private static final String VIRTUAL_ITEM_ID = "55";
	private static final String HOLD_ID = "501";
	private static final String SUPPLIER_ITEM_LOCAL_ID = "798472";
	private static final String SECOND_SUPPLYING_HOST_LMS_CODE = "koha-scenario-supplier-2";
	private static final String SECOND_SUPPLYING_LOCATION_CODE = "ab7";
	private static final String SECOND_SUPPLYING_AGENCY_CODE = "koha-scenario-supplying-agency-2";
	private static final String SECOND_SUPPLIER_ITEM_LOCAL_ID = "798473";
	private static final int SUPPLIER_ITEM_TYPE = 999;
	private static final String COMMON_PATRON_TYPE = "15";
	private static final String PICKUP_HOST_LMS_CODE = "koha-scenario-pickup";
	private static final String PICKUP_AGENCY_CODE = "koha-scenario-pickup-agency";
	private static final String BUNDLE_HOST_BARCODE = "DCB-BUNDLE-HOST";
	private static final String BUNDLE_HOST_ITEM_ID = "900";
	private static final String PICKUP_TRANSIT_STATE = "PICKUP_TRANSIT";

	@Inject private PatronFixture patronFixture;
	@Inject private PatronRequestsFixture patronRequestsFixture;
	@Inject private SupplierRequestsFixture supplierRequestsFixture;
	@Inject private HostLmsFixture hostLmsFixture;
	@Inject private AgencyFixture agencyFixture;
	@Inject private ReferenceValueMappingFixture referenceValueMappingFixture;
	@Inject private LocationFixture locationFixture;
	@Inject private ClusterRecordFixture clusterRecordFixture;
	@Inject private BibRecordFixture bibRecordFixture;
	@Inject private EventLogFixture eventLogFixture;
	@Inject private ConsortiumFixture consortiumFixture;
	@Inject private PatronRequestApiClient patronRequestApiClient;
	@Inject private AdminApiClient adminApiClient;

	private DataHostLms supplyingHostLms;
	private DataHostLms secondSupplyingHostLms;
	private MockKohaFixture koha;
	private String pickupLocationId;

	@BeforeEach
	void beforeEach(MockServerClient mockServerClient) {
		mockServerClient.reset();
		patronRequestsFixture.deleteAll();
		supplierRequestsFixture.deleteAll();
		patronFixture.deleteAllPatrons();
		locationFixture.deleteAll();
		agencyFixture.deleteAll();
		referenceValueMappingFixture.deleteAll();
		eventLogFixture.deleteAll();
		consortiumFixture.deleteAll();
		consortiumFixture.enableAllSettings();

		// A dummy's state lives in its Host LMS record, so each test starts from fresh records
		hostLmsFixture.deleteAll();
		hostLmsFixture.createKohaHostLms(KOHA_HOST_LMS_CODE, "https://" + KOHA_HOST,
			Map.of("bundle-host-barcode", BUNDLE_HOST_BARCODE));
		supplyingHostLms = hostLmsFixture.createDummyHostLms(SUPPLYING_HOST_LMS_CODE, SUPPLYING_LOCATION_CODE);
		secondSupplyingHostLms = hostLmsFixture.createDummyHostLms(SECOND_SUPPLYING_HOST_LMS_CODE, SECOND_SUPPLYING_LOCATION_CODE);

		agencyFixture.defineAgency(SUPPLYING_AGENCY_CODE, "Supplying Agency", supplyingHostLms);
		agencyFixture.defineAgency(SECOND_SUPPLYING_AGENCY_CODE, "Second Supplying Agency", secondSupplyingHostLms);
		final var borrowingAgency = agencyFixture.defineAgency(BORROWING_AGENCY_CODE, "Koha North",
			hostLmsFixture.findByCode(KOHA_HOST_LMS_CODE));

		pickupLocationId = randomUUID().toString();
		locationFixture.createPickupLocation(UUID.fromString(pickupLocationId), "North Branch",
			"PICKUP-NORTH", borrowingAgency, HOME_BRANCH);

		defineMappings();
		koha = new MockKohaFixture(mockServerClient, KOHA_HOST);
		mockKohaAsBorrower();
	}

	@AfterAll
	void afterAll() {
		patronRequestApiClient.removeTokenFromValidTokens();
	}

	@Test
	void kohaPatronBorrowsFromAnotherSystemStartToFinish() {
		koha.mockPlaceHold(placedHold().build());
		final var clusterRecordId = createClusterRecordWithOneAvailableItem();

		// The patron requests the title; Koha gets a suppressed virtual bib, a virtual item
		// and a hold for the patron at the branch they chose
		final var placed = patronRequestApiClient.placePatronRequest(clusterRecordId,
			PATRON_ID, pickupLocationId, KOHA_HOST_LMS_CODE, HOME_BRANCH);

		assertThat(placed.body(), is(notNullValue()));
		final var requestId = placed.body().getId();
		assertRequestIsInExpectedStatus(requestId, REQUEST_PLACED_AT_BORROWING_AGENCY);
		assertThat(patronRequestsFixture.findById(requestId), hasActiveWorkflow(STANDARD_WORKFLOW));
		koha.verifyHoldPlaced("{\"patron_id\": 12, \"item_id\": 55, \"pickup_library_id\": \"BRANCH-N\"}");
		koha.verifyHoldPlacedWithoutOverride();

		log.info("Supplier ships the item");
		hostLmsFixture.setDummyState(SUPPLYING_HOST_LMS_CODE, "PICKUP_TRANSIT", SUPPLIER_ROLE, RET_STD_RESPONSE_TYPE);
		patronRequestApiClient.updatePatronRequest(requestId);
		assertRequestIsInExpectedStatus(requestId, PICKUP_TRANSIT);

		log.info("Koha staff check the arriving item in at the pickup branch; Koha traps the hold");
		hostLmsFixture.setDummyState(SUPPLYING_HOST_LMS_CODE, RECEIVED_AT_PICKUP_STATE, SUPPLIER_ROLE, RET_STD_RESPONSE_TYPE);
		koha.replaceItemCirculation(VIRTUAL_BIBLIO_ID, VIRTUAL_ITEM_ID, virtualItem()
			.firstHold(KohaHoldResponse.builder().holdId(Long.valueOf(HOLD_ID)).status("W").build())
			.build());
		patronRequestApiClient.updatePatronRequest(requestId);
		hostLmsFixture.setDummyState(SUPPLYING_HOST_LMS_CODE, READY_FOR_PICKUP_STATE, SUPPLIER_ROLE, RET_STD_RESPONSE_TYPE);
		patronRequestApiClient.updatePatronRequest(requestId);
		assertRequestIsInExpectedStatus(requestId, READY_FOR_PICKUP);

		log.info("Koha staff check the item out to the patron");
		hostLmsFixture.setDummyState(SUPPLYING_HOST_LMS_CODE, LOANED_STATE, SUPPLIER_ROLE, RET_STD_RESPONSE_TYPE);
		koha.replaceItemCirculation(VIRTUAL_BIBLIO_ID, VIRTUAL_ITEM_ID, virtualItem()
			.checkedOutDate("2026-10-21")
			.checkout(KohaCheckout.builder().checkoutId(7L).dueDate("2026-11-11T23:59:00Z").renewalsCount(0).build())
			.build());
		patronRequestApiClient.updatePatronRequest(requestId);
		assertRequestIsInExpectedStatus(requestId, LOANED);

		log.info("The patron renews in Koha; DCB renews the loan at the supplier");
		koha.replaceItemCirculation(VIRTUAL_BIBLIO_ID, VIRTUAL_ITEM_ID, virtualItem()
			.checkedOutDate("2026-10-21")
			.checkout(KohaCheckout.builder().checkoutId(7L).dueDate("2026-12-02T23:59:00Z").renewalsCount(1).build())
			.build());
		patronRequestApiClient.updatePatronRequest(requestId);
		awaitRequest(requestId, "renewed once at the supplier",
			request -> Integer.valueOf(1).equals(request.getRenewalCount()));

		log.info("The owning library places a hold on its copy; DCB stops Koha renewing the virtual item");
		koha.mockItem(virtualItem().internalNotes("Virtual item = created by DCB").build());
		koha.mockUpdateItem(VIRTUAL_BIBLIO_ID, VIRTUAL_ITEM_ID, virtualItem().build());
		koha.mockCheckoutsForItem(VIRTUAL_ITEM_ID, KohaCheckout.builder()
			.checkoutId(7L).itemId(Long.valueOf(VIRTUAL_ITEM_ID)).build());
		koha.mockAllowsRenewal("7", KohaRenewability.builder().allowsRenewal(false).error("item_denied_renewal").build());
		hostLmsFixture.setDummyState(SUPPLYING_HOST_LMS_CODE, "LOANED_WITH_HOLD", SUPPLIER_ROLE, CUSTOM_RESPONSE_TYPE);
		patronRequestApiClient.updatePatronRequest(requestId);
		awaitRequest(requestId, "renewals disallowed",
			request -> request.getRenewalStatus() == PatronRequest.RenewalStatus.DISALLOWED);
		koha.verifyItemUpdated(VIRTUAL_BIBLIO_ID, VIRTUAL_ITEM_ID, "{\"collection_code\": \"DCB_NO_RENEW\"}");

		log.info("The patron returns the item and Koha staff check it in");
		hostLmsFixture.setDummyState(SUPPLYING_HOST_LMS_CODE, RETURN_TRANSIT_STATE, SUPPLIER_ROLE, RET_STD_RESPONSE_TYPE);
		koha.replaceItemCirculation(VIRTUAL_BIBLIO_ID, VIRTUAL_ITEM_ID, virtualItem().build());
		patronRequestApiClient.updatePatronRequest(requestId);
		assertRequestIsInExpectedStatus(requestId, RETURN_TRANSIT);

		log.info("The supplier has the item back");
		hostLmsFixture.setDummyState(SUPPLYING_HOST_LMS_CODE, COMPLETED_STATE, SUPPLIER_ROLE, RET_STD_RESPONSE_TYPE);
		patronRequestApiClient.updatePatronRequest(requestId);
		assertRequestIsInExpectedStatus(requestId, FINALISED);

		// Nothing is left behind in Koha but the patron
		koha.verifyHoldDeleted(HOLD_ID);
		koha.verifyItemDeleted(VIRTUAL_ITEM_ID);
		koha.verifyBibDeleted(VIRTUAL_BIBLIO_ID);
	}

	@Test
	void kohaPatronCollectsAtAThirdLibraryAndDcbChecksTheVirtualItemInOnReturn() {
		final var pickupAgency = definePickupAnywhereLibrary();
		final var puaPickupLocationId = randomUUID();
		locationFixture.createPickupLocation(puaPickupLocationId, "Elsewhere", "PICKUP-ELSEWHERE", pickupAgency);
		koha.mockPlaceHold(placedHold().pickupLibraryId("DCB-SHARING").build());
		VirtualItemLoan.reset();
		koha.answerVirtualItemLoanWith(VIRTUAL_BIBLIO_ID, VIRTUAL_ITEM_ID, VirtualItemLoan.class);
		koha.mockItem(virtualItem().build());
		koha.mockBundleHost(BUNDLE_HOST_BARCODE, Long.parseLong(BUNDLE_HOST_ITEM_ID));
		koha.mockAddToBundle(BUNDLE_HOST_ITEM_ID, virtualItem().build());
		koha.mockRemoveFromBundle(BUNDLE_HOST_ITEM_ID, VIRTUAL_ITEM_ID);
		final var clusterRecordId = createClusterRecordWithOneAvailableItem();

		// The virtual item waits at the sharing branch: the patron collects at another library
		final var placed = patronRequestApiClient.placePatronRequest(clusterRecordId,
			PATRON_ID, puaPickupLocationId.toString(), KOHA_HOST_LMS_CODE, HOME_BRANCH);

		final var requestId = placed.body().getId();
		assertRequestIsInExpectedStatus(requestId, REQUEST_PLACED_AT_PICKUP_AGENCY);
		assertThat(patronRequestsFixture.findById(requestId), hasActiveWorkflow(PICKUP_ANYWHERE_WORKFLOW));
		koha.verifyHoldPlaced("{\"patron_id\": 12, \"item_id\": 55, \"pickup_library_id\": \"DCB-SHARING\"}");

		log.info("The supplier ships to the pickup library, which receives it and puts it on its hold shelf");
		setPickupAnywhereState(PICKUP_TRANSIT_STATE);
		patronRequestApiClient.updatePatronRequest(requestId);
		assertRequestIsInExpectedStatus(requestId, PICKUP_TRANSIT);
		setPickupAnywhereState(RECEIVED_AT_PICKUP_STATE);
		patronRequestApiClient.updatePatronRequest(requestId);
		setPickupAnywhereState(READY_FOR_PICKUP_STATE);
		patronRequestApiClient.updatePatronRequest(requestId);
		assertRequestIsInExpectedStatus(requestId, READY_FOR_PICKUP);

		log.info("The patron collects it; DCB lends the virtual item to the patron in Koha");
		setPickupAnywhereState(LOANED_STATE);
		patronRequestApiClient.updatePatronRequest(requestId);
		assertRequestIsInExpectedStatus(requestId, LOANED);
		koha.verifyCheckedOut("{\"patron_id\": 12, \"item_id\": 55}");

		log.info("The patron returns it at the pickup library; DCB checks the virtual item in in Koha");
		setPickupAnywhereState(RETURN_TRANSIT_STATE);
		patronRequestApiClient.updatePatronRequest(requestId);
		assertRequestIsInExpectedStatus(requestId, RETURN_TRANSIT);
		koha.verifyAddedToBundle(BUNDLE_HOST_ITEM_ID, "{\"force_checkin\": true}");
		koha.verifyRemovedFromBundle(BUNDLE_HOST_ITEM_ID, VIRTUAL_ITEM_ID);
		koha.replaceItemCirculation(VIRTUAL_BIBLIO_ID, VIRTUAL_ITEM_ID, virtualItem().build());

		log.info("The supplier has it back");
		setPickupAnywhereState(COMPLETED_STATE);
		patronRequestApiClient.updatePatronRequest(requestId);
		assertRequestIsInExpectedStatus(requestId, FINALISED);

		koha.verifyItemDeleted(VIRTUAL_ITEM_ID);
		koha.verifyBibDeleted(VIRTUAL_BIBLIO_ID);
	}

	/**
	 * Runs inside the mock server: Koha reads the virtual item as available until DCB checks
	 * it out, and as lent from then on, as a real Koha would.
	 */
	public static class VirtualItemLoan implements ExpectationResponseCallback {
		private static final AtomicBoolean lent = new AtomicBoolean();
		private static final ObjectMapper JSON = new ObjectMapper();

		static void reset() {
			lent.set(false);
		}

		@Override
		public HttpResponse handle(HttpRequest request) {
			if ("POST".equals(request.getMethod().getValue())) {
				lent.set(true);

				return json(201, KohaCheckout.builder().checkoutId(8L).itemId(Long.valueOf(VIRTUAL_ITEM_ID)).build());
			}

			final var item = lent.get()
				? virtualItem().checkedOutDate("2026-10-22")
					.checkout(KohaCheckout.builder().checkoutId(8L).dueDate("2026-11-12T23:59:00Z").renewalsCount(0).build())
					.build()
				: virtualItem().build();

			return json(200, new KohaItem[] {item});
		}

		private static HttpResponse json(int status, Object body) {
			try {
				return HttpResponse.response().withStatusCode(status)
					.withBody(JSON.writeValueAsString(body), org.mockserver.model.MediaType.APPLICATION_JSON);
			} catch (JsonProcessingException e) {
				throw new IllegalStateException(e);
			}
		}
	}

	private DataAgency definePickupAnywhereLibrary() {
		final var pickupHostLms = hostLmsFixture.createDummyHostLms(PICKUP_HOST_LMS_CODE);
		referenceValueMappingFixture.definePatronTypeMapping("DCB", COMMON_PATRON_TYPE,
			PICKUP_HOST_LMS_CODE, COMMON_PATRON_TYPE);

		return agencyFixture.defineAgency(PICKUP_AGENCY_CODE, "Pickup Agency", pickupHostLms);
	}

	private void setPickupAnywhereState(String state) {
		hostLmsFixture.setDummyState(SUPPLYING_HOST_LMS_CODE, state, SUPPLIER_ROLE, RET_PUA_RESPONSE_TYPE);
		hostLmsFixture.setDummyState(PICKUP_HOST_LMS_CODE, state, PICKUP_ROLE, RET_PUA_RESPONSE_TYPE);
	}

	@Test
	void kohaRefusesTheBorrowerHoldAndTheReasonReachesTheAdminApi() {
		// A circulation rule the patron's library owns: the request cannot proceed, and
		// what staff need is the rule's name, not "Koha API Error"
		koha.mockHoldRefused(403, "Hold cannot be placed. Reason: tooManyReserves");
		final var clusterRecordId = createClusterRecordWithOneAvailableItem();

		final var placed = patronRequestApiClient.placePatronRequest(clusterRecordId,
			PATRON_ID, pickupLocationId, KOHA_HOST_LMS_CODE, HOME_BRANCH);

		final var requestId = placed.body().getId();
		assertRequestIsInExpectedStatus(requestId, ERROR);

		final var request = adminApiClient.getPatronRequestViaAdminApi(requestId);

		assertThat(request.getStatus().getErrorMessage(), is("Koha refused the hold"));

		final var refusal = request.getAudits().stream()
			.filter(audit -> audit.getData() != null && audit.getData().containsKey("reason"))
			.findFirst()
			.orElseThrow(() -> new AssertionError("No audit entry carries the refusal reason: " + request.getAudits()));

		assertThat(refusal.getData().get("reason"), is("tooManyReserves"));
		assertThat(String.valueOf(refusal.getData().get("remedy")), containsString("circulation rule"));
	}

	private static KohaHoldResponse.KohaHoldResponseBuilder placedHold() {
		return KohaHoldResponse.builder()
			.holdId(Long.valueOf(HOLD_ID))
			.patronId(Long.valueOf(PATRON_ID))
			.biblioId(Long.valueOf(VIRTUAL_BIBLIO_ID))
			.itemId(Long.valueOf(VIRTUAL_ITEM_ID))
			.pickupLibraryId(HOME_BRANCH);
	}

	@Test
	void kohaPatronCancelsTheHoldInKohaBeforeTheItemShips() {
		koha.mockPlaceHold(placedHold().build());
		final var clusterRecordId = createClusterRecordWithOneAvailableItem();

		final var requestId = patronRequestApiClient.placePatronRequest(clusterRecordId,
			PATRON_ID, pickupLocationId, KOHA_HOST_LMS_CODE, HOME_BRANCH).body().getId();
		assertRequestIsInExpectedStatus(requestId, REQUEST_PLACED_AT_BORROWING_AGENCY);

		log.info("The patron cancels the hold at the desk or in the OPAC");
		koha.replaceHoldWithOldHold(placedHold().cancellationDate("2026-10-22").build());
		patronRequestApiClient.updatePatronRequest(requestId);
		assertRequestIsInExpectedStatus(requestId, FINALISED);

		assertThat("The request was cancelled, then cleaned up",
			patronRequestsFixture.findAuditEntries(patronRequestsFixture.findById(requestId)).stream()
				.anyMatch(audit -> CANCELLED.equals(audit.getToStatus())), is(true));
		koha.verifyItemDeleted(VIRTUAL_ITEM_ID);
		koha.verifyBibDeleted(VIRTUAL_BIBLIO_ID);
	}

	@Test
	void theSupplierCannotSupplyAndTheVirtualItemTakesTheNextSuppliersBarcode() {
		koha.mockPlaceHold(placedHold().build());
		koha.mockNoItemWithBarcode(SECOND_SUPPLIER_ITEM_LOCAL_ID + "-i0");
		koha.mockUpdateItem(VIRTUAL_BIBLIO_ID, VIRTUAL_ITEM_ID, virtualItem().build());
		final var clusterRecordId = createClusterRecordWithTwoSuppliers();

		final var requestId = patronRequestApiClient.placePatronRequest(clusterRecordId,
			PATRON_ID, pickupLocationId, KOHA_HOST_LMS_CODE, HOME_BRANCH).body().getId();
		assertRequestIsInExpectedStatus(requestId, REQUEST_PLACED_AT_BORROWING_AGENCY);

		// Either supplier may be chosen first; the other is the one re-resolution must reach
		final var firstSupplier = activeSupplierOf(requestId);
		final var secondSupplier = SUPPLYING_HOST_LMS_CODE.equals(firstSupplier)
			? SECOND_SUPPLYING_HOST_LMS_CODE : SUPPLYING_HOST_LMS_CODE;
		final var secondSuppliersBarcode = (SUPPLYING_HOST_LMS_CODE.equals(firstSupplier)
			? SECOND_SUPPLIER_ITEM_LOCAL_ID : SUPPLIER_ITEM_LOCAL_ID) + "-i0";

		log.info("{} cannot supply: its hold goes missing", firstSupplier);
		hostLmsFixture.setDummyState(firstSupplier, "MISSING", SUPPLIER_ROLE, CUSTOM_RESPONSE_TYPE);
		patronRequestApiClient.updatePatronRequest(requestId);
		awaitRequest(requestId, "re-resolved to " + secondSupplier,
			request -> REQUEST_PLACED_AT_BORROWING_AGENCY.equals(request.getStatus())
				&& secondSupplier.equals(activeSupplierOf(requestId)));

		koha.verifyItemUpdated(VIRTUAL_BIBLIO_ID, VIRTUAL_ITEM_ID,
			"{\"external_id\": \"" + secondSuppliersBarcode + "\"}");
	}

	private String activeSupplierOf(UUID requestId) {
		return supplierRequestsFixture.findFor(patronRequestsFixture.findById(requestId)).getHostLmsCode();
	}

	private void mockKohaAsBorrower() {
		koha.mockPatron(KohaPatron.builder()
			.patronId(Long.valueOf(PATRON_ID))
			.cardnumber("CARD-0001")
			.surname("Example")
			.firstname("Grace")
			.libraryId(HOME_BRANCH)
			.categoryId("AD")
			.expiryDate("2099-12-31")
			.build());
		koha.mockNoActiveHolds();
		// Resolution checks whether the supplier's copy is already a virtual item here
		koha.mockNoItemWithBarcode(SUPPLIER_ITEM_LOCAL_ID + "-i0");
		koha.mockCreateBib(KohaBiblio.builder().biblioId(Integer.valueOf(VIRTUAL_BIBLIO_ID)).build());
		koha.mockCreateItem(VIRTUAL_BIBLIO_ID, virtualItem().build());
		koha.mockHold(KohaHoldResponse.builder()
			.holdId(Long.valueOf(HOLD_ID))
			.patronId(Long.valueOf(PATRON_ID))
			.biblioId(Long.valueOf(VIRTUAL_BIBLIO_ID))
			.itemId(Long.valueOf(VIRTUAL_ITEM_ID))
			.pickupLibraryId(HOME_BRANCH)
			.build());
		koha.mockItemCirculation(VIRTUAL_BIBLIO_ID, VIRTUAL_ITEM_ID, virtualItem().build());
		koha.mockDeleteHold(HOLD_ID);
		koha.mockDeleteItem(VIRTUAL_ITEM_ID);
		koha.mockDeleteBib(VIRTUAL_BIBLIO_ID);
	}

	private static KohaItem.KohaItemBuilder virtualItem() {
		return KohaItem.builder()
			.itemId(Long.valueOf(VIRTUAL_ITEM_ID))
			.biblioId(Long.valueOf(VIRTUAL_BIBLIO_ID))
			.externalId("supplier-barcode")
			.homeLibraryId(HOME_BRANCH)
			.holdingLibraryId(HOME_BRANCH)
			.itemTypeId("DCB_VIRTUAL")
			.callnumber("DCB_VIRTUAL_COLLECTION")
			.notForLoanStatus(0)
			.effectiveNotForLoanStatus(0)
			.lostStatus(0)
			.withdrawn(0);
	}

	private UUID createClusterRecordWithOneAvailableItem() {
		final var clusterRecordId = randomUUID();
		final var clusterRecord = clusterRecordFixture.createClusterRecord(clusterRecordId, clusterRecordId);
		bibRecordFixture.createBibRecord(clusterRecordId, supplyingHostLms.getId(),
			SUPPLIER_ITEM_LOCAL_ID, clusterRecord);
		return clusterRecordId;
	}

	private UUID createClusterRecordWithTwoSuppliers() {
		final var clusterRecordId = randomUUID();
		final var clusterRecord = clusterRecordFixture.createClusterRecord(clusterRecordId, clusterRecordId);
		bibRecordFixture.createBibRecord(clusterRecordId, supplyingHostLms.getId(),
			SUPPLIER_ITEM_LOCAL_ID, clusterRecord);
		bibRecordFixture.createBibRecord(randomUUID(), secondSupplyingHostLms.getId(),
			SECOND_SUPPLIER_ITEM_LOCAL_ID, clusterRecord);
		return clusterRecordId;
	}

	private void defineMappings() {
		referenceValueMappingFixture.defineLocationToAgencyMapping(SUPPLYING_HOST_LMS_CODE,
			SUPPLYING_LOCATION_CODE, SUPPLYING_AGENCY_CODE);
		referenceValueMappingFixture.defineLocationToAgencyMapping(SECOND_SUPPLYING_HOST_LMS_CODE,
			SECOND_SUPPLYING_LOCATION_CODE, SECOND_SUPPLYING_AGENCY_CODE);
		referenceValueMappingFixture.defineLocationToAgencyMapping(KOHA_HOST_LMS_CODE,
			HOME_BRANCH, BORROWING_AGENCY_CODE);

		referenceValueMappingFixture.defineLocalToCanonicalItemTypeRangeMapping(
			SUPPLYING_HOST_LMS_CODE, SUPPLIER_ITEM_TYPE, SUPPLIER_ITEM_TYPE, "loanable-item");
		referenceValueMappingFixture.defineLocalToCanonicalItemTypeRangeMapping(
			SECOND_SUPPLYING_HOST_LMS_CODE, SUPPLIER_ITEM_TYPE, SUPPLIER_ITEM_TYPE, "loanable-item");
		// The supplier's copy reaches the borrower as the canonical type CIRC
		referenceValueMappingFixture.defineCanonicalToLocalItemTypeMapping(
			KOHA_HOST_LMS_CODE, "CIRC", "DCB_VIRTUAL");

		referenceValueMappingFixture.definePatronTypeMapping(KOHA_HOST_LMS_CODE, "AD", "DCB", COMMON_PATRON_TYPE);
		referenceValueMappingFixture.definePatronTypeMapping("DCB", COMMON_PATRON_TYPE,
			SUPPLYING_HOST_LMS_CODE, COMMON_PATRON_TYPE);
		referenceValueMappingFixture.definePatronTypeMapping("DCB", COMMON_PATRON_TYPE,
			SECOND_SUPPLYING_HOST_LMS_CODE, COMMON_PATRON_TYPE);
	}

	private void assertRequestIsInExpectedStatus(UUID requestId, PatronRequest.Status expectedStatus) {
		awaitRequest(requestId, "in status " + expectedStatus, request -> expectedStatus.equals(request.getStatus()));
	}

	private void awaitRequest(UUID requestId, String expectation, Predicate<PatronRequest> condition) {
		await().alias(expectation).atMost(30, SECONDS).until(() -> {
			final var request = patronRequestsFixture.findById(requestId);
			log.debug("Request {} is {} (renewals {}, {})", requestId, request.getStatus(),
				request.getRenewalCount(), request.getRenewalStatus());
			return condition.test(request);
		});
	}
}
