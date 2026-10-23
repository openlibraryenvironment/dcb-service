package org.olf.dcb.api;

import static java.util.UUID.randomUUID;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.olf.dcb.core.model.PatronRequest.Status.FINALISED;
import static org.olf.dcb.core.model.PatronRequest.Status.LOANED;
import static org.olf.dcb.core.model.PatronRequest.Status.PICKUP_TRANSIT;
import static org.olf.dcb.core.model.PatronRequest.Status.READY_FOR_PICKUP;
import static org.olf.dcb.core.model.PatronRequest.Status.REQUEST_PLACED_AT_BORROWING_AGENCY;
import static org.olf.dcb.core.model.PatronRequest.Status.RETURN_TRANSIT;
import static org.olf.dcb.core.model.WorkflowConstants.STANDARD_WORKFLOW;
import static org.olf.dcb.devtools.interaction.dummy.responder.DummyConstants.COMPLETED_STATE;
import static org.olf.dcb.devtools.interaction.dummy.responder.DummyConstants.LOANED_STATE;
import static org.olf.dcb.devtools.interaction.dummy.responder.DummyConstants.READY_FOR_PICKUP_STATE;
import static org.olf.dcb.devtools.interaction.dummy.responder.DummyConstants.RECEIVED_AT_PICKUP_STATE;
import static org.olf.dcb.devtools.interaction.dummy.responder.DummyConstants.RETURN_TRANSIT_STATE;
import static org.olf.dcb.devtools.interaction.dummy.responder.DummyConstants.RET_STD_RESPONSE_TYPE;
import static org.olf.dcb.devtools.interaction.dummy.responder.DummyConstants.SUPPLIER_ROLE;
import static org.olf.dcb.test.matchers.PatronRequestMatchers.hasActiveWorkflow;

import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
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
	private static final int SUPPLIER_ITEM_TYPE = 999;
	private static final String COMMON_PATRON_TYPE = "15";

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

	private DataHostLms supplyingHostLms;
	private MockKohaFixture koha;
	private String pickupLocationId;

	@BeforeAll
	void beforeAll() {
		hostLmsFixture.deleteAll();
		hostLmsFixture.createKohaHostLms(KOHA_HOST_LMS_CODE, "https://" + KOHA_HOST);
		supplyingHostLms = hostLmsFixture.createDummyHostLms(SUPPLYING_HOST_LMS_CODE, SUPPLYING_LOCATION_CODE);
	}

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

		agencyFixture.defineAgency(SUPPLYING_AGENCY_CODE, "Supplying Agency", supplyingHostLms);
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
		koha.mockPlaceHold(KohaHoldResponse.builder()
			.holdId(Long.valueOf(HOLD_ID))
			.patronId(Long.valueOf(PATRON_ID))
			.biblioId(Long.valueOf(VIRTUAL_BIBLIO_ID))
			.itemId(Long.valueOf(VIRTUAL_ITEM_ID))
			.pickupLibraryId(HOME_BRANCH)
			.build());
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

	private void defineMappings() {
		referenceValueMappingFixture.defineLocationToAgencyMapping(SUPPLYING_HOST_LMS_CODE,
			SUPPLYING_LOCATION_CODE, SUPPLYING_AGENCY_CODE);
		referenceValueMappingFixture.defineLocationToAgencyMapping(KOHA_HOST_LMS_CODE,
			HOME_BRANCH, BORROWING_AGENCY_CODE);

		referenceValueMappingFixture.defineLocalToCanonicalItemTypeRangeMapping(
			SUPPLYING_HOST_LMS_CODE, SUPPLIER_ITEM_TYPE, SUPPLIER_ITEM_TYPE, "loanable-item");
		// The supplier's copy reaches the borrower as the canonical type CIRC
		referenceValueMappingFixture.defineCanonicalToLocalItemTypeMapping(
			KOHA_HOST_LMS_CODE, "CIRC", "DCB_VIRTUAL");

		referenceValueMappingFixture.definePatronTypeMapping(KOHA_HOST_LMS_CODE, "AD", "DCB", COMMON_PATRON_TYPE);
		referenceValueMappingFixture.definePatronTypeMapping("DCB", COMMON_PATRON_TYPE,
			SUPPLYING_HOST_LMS_CODE, COMMON_PATRON_TYPE);
	}

	private void assertRequestIsInExpectedStatus(UUID requestId, PatronRequest.Status expectedStatus) {
		await().atMost(30, SECONDS).until(() -> {
			final var status = patronRequestsFixture.findById(requestId).getStatus();
			log.debug("Request {} is {}", requestId, status);
			return expectedStatus.equals(status);
		});
	}
}
