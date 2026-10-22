package org.olf.dcb.api;

import static java.util.UUID.randomUUID;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.olf.dcb.core.model.PatronRequest.Status.CANCELLED;
import static org.olf.dcb.core.model.PatronRequest.Status.FINALISED;
import static org.olf.dcb.core.model.PatronRequest.Status.LOANED;
import static org.olf.dcb.core.model.PatronRequest.Status.PICKUP_TRANSIT;
import static org.olf.dcb.core.model.PatronRequest.Status.READY_FOR_PICKUP;
import static org.olf.dcb.core.model.PatronRequest.Status.REQUEST_PLACED_AT_BORROWING_AGENCY;
import static org.olf.dcb.core.model.PatronRequest.Status.RETURN_TRANSIT;
import static org.olf.dcb.core.model.WorkflowConstants.STANDARD_WORKFLOW;
import static org.olf.dcb.devtools.interaction.dummy.responder.DummyConstants.BORROWER_ROLE;
import static org.olf.dcb.devtools.interaction.dummy.responder.DummyConstants.COMPLETED_STATE;
import static org.olf.dcb.devtools.interaction.dummy.responder.DummyConstants.CUSTOM_RESPONSE_TYPE;
import static org.olf.dcb.devtools.interaction.dummy.responder.DummyConstants.LOANED_STATE;
import static org.olf.dcb.devtools.interaction.dummy.responder.DummyConstants.READY_FOR_PICKUP_STATE;
import static org.olf.dcb.devtools.interaction.dummy.responder.DummyConstants.RECEIVED_AT_PICKUP_STATE;
import static org.olf.dcb.devtools.interaction.dummy.responder.DummyConstants.RETURN_TRANSIT_STATE;
import static org.olf.dcb.devtools.interaction.dummy.responder.DummyConstants.RET_STD_RESPONSE_TYPE;
import static org.olf.dcb.devtools.interaction.dummy.responder.DummyConstants.SUPPLIER_ROLE;
import static org.olf.dcb.test.matchers.PatronRequestMatchers.hasActiveWorkflow;

import java.util.UUID;
import java.util.function.Predicate;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockserver.client.MockServerClient;
import org.olf.dcb.core.interaction.koha.MockKohaFixture;
import org.olf.dcb.core.interaction.koha.dto.KohaCheckout;
import org.olf.dcb.core.interaction.koha.dto.KohaHoldResponse;
import org.olf.dcb.core.interaction.koha.dto.KohaItem;
import org.olf.dcb.core.interaction.koha.dto.KohaPatron;
import org.olf.dcb.core.interaction.koha.dto.KohaTransfer;
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
 * A Koha library lends to another system. The borrower is the dummy Host LMS; the
 * supplier is a Koha behind the mock REST API. What moves the request on the Koha side
 * is what Koha staff do: pull the hold and check the item in (Koha sends it in transit to
 * the sharing branch), and check it in again when it comes home. DCB itself lends the item
 * to its virtual patron when the borrower lends it to the real one, confirming the
 * in-transit prompt Koha would show staff.
 */
@MockServerMicronautTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Slf4j
class KohaSupplierScenarioTests {
	private static final String KOHA_HOST_LMS_CODE = "koha-scenario-lender";
	private static final String KOHA_HOST = "fake-koha-lender";
	private static final String BORROWING_HOST_LMS_CODE = "koha-dummy-borrower";
	private static final String LENDING_AGENCY_CODE = "koha-lending-agency";
	private static final String BORROWING_AGENCY_CODE = "koha-dummy-borrowing-agency";
	private static final String LENDING_BRANCH = "BRANCH-S";
	private static final String BIBLIO_ID = "3001";
	private static final String ITEM_ID = "71";
	private static final String ITEM_BARCODE = "KOHA-0071";
	private static final String VIRTUAL_PATRON_ID = "90";
	private static final String HOLD_ID = "601";
	private static final String CHECKOUT_ID = "7";
	private static final String CONFIRMATION_TOKEN = "confirmation-jwt";
	private static final String BORROWING_PATRON_LOCAL_ID = "872321";
	private static final String COMMON_PATRON_TYPE = "15";
	private static final String PICKUP_TRANSIT_STATE = "PICKUP_TRANSIT";
	private static final String DUMMY_LENDER_HOST_LMS_CODE = "koha-scenario-dummy-lender";
	private static final String DUMMY_LENDER_LOCATION_CODE = "ab7";
	private static final String DUMMY_LENDER_AGENCY_CODE = "koha-dummy-lending-agency";
	private static final String DUMMY_LENDER_ITEM_LOCAL_ID = "798474";
	private static final int DUMMY_LENDER_ITEM_TYPE = 999;

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

	private DataHostLms kohaHostLms;
	private DataHostLms borrowingHostLms;
	private DataHostLms dummyLenderHostLms;
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
		kohaHostLms = hostLmsFixture.createKohaHostLms(KOHA_HOST_LMS_CODE, "https://" + KOHA_HOST);
		borrowingHostLms = hostLmsFixture.createDummyHostLms(BORROWING_HOST_LMS_CODE);
		dummyLenderHostLms = hostLmsFixture.createDummyHostLms(DUMMY_LENDER_HOST_LMS_CODE, DUMMY_LENDER_LOCATION_CODE);

		agencyFixture.defineAgency(LENDING_AGENCY_CODE, "Koha South", kohaHostLms);
		agencyFixture.defineAgency(DUMMY_LENDER_AGENCY_CODE, "Other Lender", dummyLenderHostLms);
		final var borrowingAgency = agencyFixture.defineAgency(BORROWING_AGENCY_CODE, "Borrowing Agency", borrowingHostLms);

		pickupLocationId = randomUUID().toString();
		locationFixture.createPickupLocation(UUID.fromString(pickupLocationId), "ABC123", "ABC123", borrowingAgency);

		defineMappings();
		koha = new MockKohaFixture(mockServerClient, KOHA_HOST);
		mockKohaAsLender();
	}

	@AfterAll
	void afterAll() {
		patronRequestApiClient.removeTokenFromValidTokens();
	}

	@Test
	void kohaLendsToAnotherSystemStartToFinish() {
		koha.mockDeleteHoldGone(HOLD_ID);
		final var clusterRecordId = createClusterRecordWithKohaItem();

		// The patron requests the title; the Koha copy is chosen, a virtual patron is created
		// at the sharing branch, and a hold on the real item is placed for it, collected at
		// the sharing branch, over any block on the virtual patron
		final var placed = patronRequestApiClient.placePatronRequest(clusterRecordId,
			BORROWING_PATRON_LOCAL_ID, pickupLocationId, BORROWING_HOST_LMS_CODE, "HOME_LIBRARY_CODE");

		assertThat(placed.body(), is(notNullValue()));
		final var requestId = placed.body().getId();
		assertRequestIsInExpectedStatus(requestId, REQUEST_PLACED_AT_BORROWING_AGENCY);
		assertThat(patronRequestsFixture.findById(requestId), hasActiveWorkflow(STANDARD_WORKFLOW));
		koha.verifyPatronCreated("{\"library_id\": \"DCB-SHARING\", \"category_id\": \"DCB\"}");
		koha.verifyHoldPlaced("{\"patron_id\": 90, \"item_id\": 71, \"pickup_library_id\": \"DCB-SHARING\"}");
		koha.verifyHoldPlacedOverridingPatronBlocks();
		koha.verifyNoVirtualRecordsCreated();

		log.info("Koha staff pull the hold and check the item in; Koha sends it in transit to the sharing branch");
		koha.replaceHold(hold().status("T").build());
		koha.replaceItemCirculation(BIBLIO_ID, ITEM_ID, kohaItem()
			.transfer(KohaTransfer.builder()
				.transferId(31L)
				.fromLibraryId(LENDING_BRANCH)
				.toLibraryId("DCB-SHARING")
				.dateSent("2026-10-22T10:00:00Z")
				.build())
			.build());
		hostLmsFixture.setDummyState(BORROWING_HOST_LMS_CODE, PICKUP_TRANSIT_STATE, BORROWER_ROLE, RET_STD_RESPONSE_TYPE);
		patronRequestApiClient.updatePatronRequest(requestId);
		assertRequestIsInExpectedStatus(requestId, PICKUP_TRANSIT);

		log.info("The borrowing library receives the item and puts it on its hold shelf");
		hostLmsFixture.setDummyState(BORROWING_HOST_LMS_CODE, RECEIVED_AT_PICKUP_STATE, BORROWER_ROLE, RET_STD_RESPONSE_TYPE);
		patronRequestApiClient.updatePatronRequest(requestId);
		hostLmsFixture.setDummyState(BORROWING_HOST_LMS_CODE, READY_FOR_PICKUP_STATE, BORROWER_ROLE, RET_STD_RESPONSE_TYPE);
		patronRequestApiClient.updatePatronRequest(requestId);
		assertRequestIsInExpectedStatus(requestId, READY_FOR_PICKUP);

		log.info("The borrowing library lends the item; DCB lends the real one to its virtual patron, confirming the in-transit prompt");
		hostLmsFixture.setDummyState(BORROWING_HOST_LMS_CODE, LOANED_STATE, BORROWER_ROLE, RET_STD_RESPONSE_TYPE);
		patronRequestApiClient.updatePatronRequest(requestId);
		assertRequestIsInExpectedStatus(requestId, LOANED);
		koha.verifyCheckedOut("{\"patron_id\": 90, \"item_id\": 71}");
		koha.verifyCheckoutConfirmedWith(CONFIRMATION_TOKEN);

		// Koha has filled the hold and the item is on loan to the virtual patron
		koha.replaceHoldWithOldHold(hold().status("F").build());
		koha.replaceItemCirculation(BIBLIO_ID, ITEM_ID, loanedItem().build());

		log.info("The borrowing patron renews; DCB renews the virtual patron's loan in Koha");
		koha.mockCheckoutsForItem(ITEM_ID, KohaCheckout.builder()
			.checkoutId(Long.valueOf(CHECKOUT_ID)).itemId(Long.valueOf(ITEM_ID)).build());
		koha.mockRenewal(CHECKOUT_ID, KohaCheckout.builder()
			.checkoutId(Long.valueOf(CHECKOUT_ID)).dueDate("2026-12-10T23:59:00Z").renewalsCount(1).build());
		hostLmsFixture.setDummyState(BORROWING_HOST_LMS_CODE, "LOANED_RENEWED", BORROWER_ROLE, CUSTOM_RESPONSE_TYPE);
		patronRequestApiClient.updatePatronRequest(requestId);
		awaitRequest(requestId, "renewed once in Koha",
			request -> Integer.valueOf(1).equals(request.getRenewalCount()));
		koha.verifyRenewed(CHECKOUT_ID);
		// A renewal is only reflected while renewals are allowed, so this also proves the
		// lifetime hold tally on the item was not read as a hold
		assertThat(patronRequestsFixture.findById(requestId).getRenewalStatus(),
			is(PatronRequest.RenewalStatus.ALLOWED));

		log.info("A patron of the lending library places a hold on the copy; DCB stops the borrower renewing");
		koha.replaceItemCirculation(BIBLIO_ID, ITEM_ID, loanedItem()
			.firstHold(KohaHoldResponse.builder().holdId(602L).patronId(13L).itemId(Long.valueOf(ITEM_ID)).build())
			.build());
		patronRequestApiClient.updatePatronRequest(requestId);
		awaitRequest(requestId, "renewals disallowed",
			request -> request.getRenewalStatus() == PatronRequest.RenewalStatus.DISALLOWED);

		log.info("The patron returns the item and the borrowing library sends it back");
		hostLmsFixture.setDummyState(BORROWING_HOST_LMS_CODE, RETURN_TRANSIT_STATE, BORROWER_ROLE, RET_STD_RESPONSE_TYPE);
		patronRequestApiClient.updatePatronRequest(requestId);
		assertRequestIsInExpectedStatus(requestId, RETURN_TRANSIT);

		log.info("Koha staff check the returned item in; Koha closes the virtual patron's loan");
		koha.replaceItemCirculation(BIBLIO_ID, ITEM_ID, kohaItem().build());
		hostLmsFixture.setDummyState(BORROWING_HOST_LMS_CODE, COMPLETED_STATE, BORROWER_ROLE, RET_STD_RESPONSE_TYPE);
		patronRequestApiClient.updatePatronRequest(requestId);
		assertRequestIsInExpectedStatus(requestId, FINALISED);

		// The virtual patron stays for the next loan; the cleanup's delete of the filled hold
		// met Koha's 404 and counted as done; nothing virtual was ever created on the lender
		koha.verifyPatronNeverDeleted(VIRTUAL_PATRON_ID);
		koha.verifyHoldDeleted(HOLD_ID);
		koha.verifyNoVirtualRecordsCreated();
	}

	@Test
	void theBorrowingPatronCancelsAndKohaIsToldToDropTheHold() {
		koha.mockDeleteHold(HOLD_ID);
		final var clusterRecordId = createClusterRecordWithKohaItem();

		final var requestId = patronRequestApiClient.placePatronRequest(clusterRecordId,
			BORROWING_PATRON_LOCAL_ID, pickupLocationId, BORROWING_HOST_LMS_CODE, "HOME_LIBRARY_CODE").body().getId();
		assertRequestIsInExpectedStatus(requestId, REQUEST_PLACED_AT_BORROWING_AGENCY);

		log.info("The patron cancels at the borrowing library before Koha staff have acted");
		hostLmsFixture.setDummyState(BORROWING_HOST_LMS_CODE, "CANCELLED", BORROWER_ROLE, CUSTOM_RESPONSE_TYPE);
		patronRequestApiClient.updatePatronRequest(requestId);
		assertRequestIsInExpectedStatus(requestId, FINALISED);

		assertThat("The request was cancelled, then cleaned up",
			patronRequestsFixture.findAuditEntries(patronRequestsFixture.findById(requestId)).stream()
				.anyMatch(audit -> CANCELLED.equals(audit.getToStatus())), is(true));
		koha.verifyHoldDeleted(HOLD_ID);
		koha.verifyPatronNeverDeleted(VIRTUAL_PATRON_ID);
	}

	@Test
	void whenTheChosenSupplierCannotSupplyTheRequestMovesToTheOther() {
		koha.mockDeleteHold(HOLD_ID);
		final var clusterRecordId = createClusterRecordWithKohaAndDummyItems();

		final var requestId = patronRequestApiClient.placePatronRequest(clusterRecordId,
			BORROWING_PATRON_LOCAL_ID, pickupLocationId, BORROWING_HOST_LMS_CODE, "HOME_LIBRARY_CODE").body().getId();
		assertRequestIsInExpectedStatus(requestId, REQUEST_PLACED_AT_BORROWING_AGENCY);

		// Either lender may be chosen first, and each direction is a Koha re-resolution case
		if (KOHA_HOST_LMS_CODE.equals(activeSupplierOf(requestId))) {
			log.info("Koha staff cancel the hold; the other lender is asked");
			koha.replaceHoldWithOldHold(hold().cancellationDate("2026-10-22").build());
			patronRequestApiClient.updatePatronRequest(requestId);
			awaitRequest(requestId, "re-resolved to the other lender",
				request -> REQUEST_PLACED_AT_BORROWING_AGENCY.equals(request.getStatus())
					&& DUMMY_LENDER_HOST_LMS_CODE.equals(activeSupplierOf(requestId)));
		} else {
			log.info("The other lender cannot supply; Koha is asked");
			hostLmsFixture.setDummyState(DUMMY_LENDER_HOST_LMS_CODE, "MISSING", SUPPLIER_ROLE, CUSTOM_RESPONSE_TYPE);
			patronRequestApiClient.updatePatronRequest(requestId);
			awaitRequest(requestId, "re-resolved to Koha",
				request -> REQUEST_PLACED_AT_BORROWING_AGENCY.equals(request.getStatus())
					&& KOHA_HOST_LMS_CODE.equals(activeSupplierOf(requestId)));
			koha.verifyHoldPlaced("{\"patron_id\": 90, \"item_id\": 71, \"pickup_library_id\": \"DCB-SHARING\"}");
		}
	}

	private String activeSupplierOf(UUID requestId) {
		return supplierRequestsFixture.findFor(patronRequestsFixture.findById(requestId)).getHostLmsCode();
	}

	private void mockKohaAsLender() {
		koha.mockItemsPage(BIBLIO_ID, 1, kohaItem().build());
		koha.mockItemCirculation(BIBLIO_ID, ITEM_ID, kohaItem().build());
		koha.mockNoActiveHolds();
		koha.mockNoPatronWithAnyCardnumber();
		koha.mockCreatePatron(virtualPatron());
		koha.mockPatron(virtualPatron());
		koha.mockPlaceHold(hold().build());
		koha.mockHold(hold().build());
		koha.mockCheckoutConfirmed(CONFIRMATION_TOKEN, KohaCheckout.builder()
			.checkoutId(Long.valueOf(CHECKOUT_ID))
			.patronId(Long.valueOf(VIRTUAL_PATRON_ID))
			.itemId(Long.valueOf(ITEM_ID))
			.dueDate("2026-11-19T23:59:00Z")
			.build());
		koha.mockCheckoutNeedsConfirmation();
		koha.mockCheckoutAvailability(VIRTUAL_PATRON_ID, ITEM_ID,
			"{\"blockers\": {}, \"confirms\": {\"TRANSFERRED\": 1}, \"warnings\": {}, \"confirmation_token\": \""
				+ CONFIRMATION_TOKEN + "\"}");
	}

	private static KohaItem.KohaItemBuilder kohaItem() {
		return KohaItem.builder()
			.itemId(Long.valueOf(ITEM_ID))
			.biblioId(Long.valueOf(BIBLIO_ID))
			.externalId(ITEM_BARCODE)
			.homeLibraryId(LENDING_BRANCH)
			.holdingLibraryId(LENDING_BRANCH)
			.itemTypeId("BK")
			.location("STACKS")
			.callnumber("823.912 JOY")
			// Koha's lifetime tally, which the virtual patron's own hold is part of
			.holdCount(1)
			.notForLoanStatus(0)
			.effectiveNotForLoanStatus(0)
			.lostStatus(0)
			.withdrawn(0);
	}

	private static KohaItem.KohaItemBuilder loanedItem() {
		return kohaItem()
			.checkedOutDate("2026-10-29")
			.checkout(KohaCheckout.builder()
				.checkoutId(Long.valueOf(CHECKOUT_ID))
				.patronId(Long.valueOf(VIRTUAL_PATRON_ID))
				.dueDate("2026-11-19T23:59:00Z")
				.renewalsCount(0)
				.build());
	}

	private static KohaHoldResponse.KohaHoldResponseBuilder hold() {
		return KohaHoldResponse.builder()
			.holdId(Long.valueOf(HOLD_ID))
			.patronId(Long.valueOf(VIRTUAL_PATRON_ID))
			.biblioId(Long.valueOf(BIBLIO_ID))
			.itemId(Long.valueOf(ITEM_ID))
			.pickupLibraryId("DCB-SHARING");
	}

	private static KohaPatron virtualPatron() {
		return KohaPatron.builder()
			.patronId(Long.valueOf(VIRTUAL_PATRON_ID))
			.cardnumber(BORROWING_PATRON_LOCAL_ID + "@" + BORROWING_AGENCY_CODE)
			.surname("Patron")
			.firstname("Borrowing")
			.libraryId("DCB-SHARING")
			.categoryId("DCB")
			.expiryDate("2099-12-31")
			.build();
	}

	private UUID createClusterRecordWithKohaItem() {
		final var clusterRecordId = randomUUID();
		final var clusterRecord = clusterRecordFixture.createClusterRecord(clusterRecordId, clusterRecordId);
		bibRecordFixture.createBibRecord(clusterRecordId, kohaHostLms.getId(), BIBLIO_ID, clusterRecord);
		return clusterRecordId;
	}

	private UUID createClusterRecordWithKohaAndDummyItems() {
		final var clusterRecordId = randomUUID();
		final var clusterRecord = clusterRecordFixture.createClusterRecord(clusterRecordId, clusterRecordId);
		bibRecordFixture.createBibRecord(clusterRecordId, kohaHostLms.getId(), BIBLIO_ID, clusterRecord);
		bibRecordFixture.createBibRecord(randomUUID(), dummyLenderHostLms.getId(),
			DUMMY_LENDER_ITEM_LOCAL_ID, clusterRecord);
		return clusterRecordId;
	}

	private void defineMappings() {
		referenceValueMappingFixture.defineLocationToAgencyMapping(KOHA_HOST_LMS_CODE,
			LENDING_BRANCH, LENDING_AGENCY_CODE);
		referenceValueMappingFixture.defineLocationToAgencyMapping(BORROWING_HOST_LMS_CODE,
			"TR", BORROWING_AGENCY_CODE);

		referenceValueMappingFixture.defineLocalToCanonicalItemTypeMapping(KOHA_HOST_LMS_CODE, "BK", "loanable-item");

		referenceValueMappingFixture.defineLocationToAgencyMapping(DUMMY_LENDER_HOST_LMS_CODE,
			DUMMY_LENDER_LOCATION_CODE, DUMMY_LENDER_AGENCY_CODE);
		referenceValueMappingFixture.defineLocalToCanonicalItemTypeRangeMapping(
			DUMMY_LENDER_HOST_LMS_CODE, DUMMY_LENDER_ITEM_TYPE, DUMMY_LENDER_ITEM_TYPE, "loanable-item");
		referenceValueMappingFixture.definePatronTypeMapping("DCB", COMMON_PATRON_TYPE, DUMMY_LENDER_HOST_LMS_CODE, COMMON_PATRON_TYPE);

		// The borrowing patron's type reaches Koha as the category the virtual patron is created in
		referenceValueMappingFixture.definePatronTypeMapping("DCB", COMMON_PATRON_TYPE, KOHA_HOST_LMS_CODE, "DCB");
		referenceValueMappingFixture.definePatronTypeMapping(BORROWING_HOST_LMS_CODE, "STD", "DCB", COMMON_PATRON_TYPE);
		referenceValueMappingFixture.defineNumericPatronTypeRangeMapping(BORROWING_HOST_LMS_CODE,
			Integer.parseInt(COMMON_PATRON_TYPE), Integer.parseInt(COMMON_PATRON_TYPE), "DCB", COMMON_PATRON_TYPE);
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
