package org.olf.dcb.api;

import static java.util.UUID.randomUUID;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.olf.dcb.core.model.PatronRequest.Status.HANDED_OFF_AS_LOCAL;
import static org.olf.dcb.core.model.WorkflowConstants.LOCAL_WORKFLOW;
import static org.olf.dcb.test.matchers.PatronRequestMatchers.hasActiveWorkflow;

import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockserver.client.MockServerClient;
import org.olf.dcb.core.interaction.koha.MockKohaFixture;
import org.olf.dcb.core.interaction.koha.dto.KohaHoldResponse;
import org.olf.dcb.core.interaction.koha.dto.KohaItem;
import org.olf.dcb.core.interaction.koha.dto.KohaPatron;
import org.olf.dcb.core.model.DataHostLms;
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
 * A Koha patron requests an item their own Koha holds (RET-LOCAL).
 * <p>
 * One hold on the real item for the real patron at the branch they chose, with no
 * virtual bib, item or patron and no override of the patron's own blocks; Koha confirms
 * it, and the request is handed off as a local hold that Koha and the library manage.
 */
@MockServerMicronautTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Slf4j
class KohaLocalRequestScenarioTests {
	private static final String KOHA_HOST_LMS_CODE = "koha-local-scenario";
	private static final String KOHA_HOST = "fake-koha-local";
	private static final String AGENCY_CODE = "koha-local-agency";
	private static final String HOME_BRANCH = "BRANCH-N";
	private static final String PATRON_ID = "12";
	private static final String BIBLIO_ID = "4001";
	private static final String SOUTH_BIBLIO_ID = "4002";
	private static final String ITEM_ID = "55";
	private static final String HOLD_ID = "501";
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

	private DataHostLms kohaHostLms;
	private MockKohaFixture koha;
	private String pickupLocationId;

	@BeforeAll
	void beforeAll() {
		hostLmsFixture.deleteAll();
		kohaHostLms = hostLmsFixture.createKohaHostLms(KOHA_HOST_LMS_CODE, "https://" + KOHA_HOST);
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
		// Own-library borrowing is what lets a patron's own copy be chosen at all
		consortiumFixture.enableAllSettings();

		final var agency = agencyFixture.defineAgency(AGENCY_CODE, "Koha North", kohaHostLms);
		pickupLocationId = randomUUID().toString();
		locationFixture.createPickupLocation(UUID.fromString(pickupLocationId), "North Branch",
			"PICKUP-NORTH", agency, HOME_BRANCH);

		referenceValueMappingFixture.defineLocationToAgencyMapping(KOHA_HOST_LMS_CODE, HOME_BRANCH, AGENCY_CODE);
		referenceValueMappingFixture.defineLocalToCanonicalItemTypeMapping(KOHA_HOST_LMS_CODE, "BK", "CIRC");
		referenceValueMappingFixture.definePatronTypeMapping(KOHA_HOST_LMS_CODE, "AD", "DCB", COMMON_PATRON_TYPE);

		koha = new MockKohaFixture(mockServerClient, KOHA_HOST);
		mockKoha();
	}

	@AfterAll
	void afterAll() {
		patronRequestApiClient.removeTokenFromValidTokens();
	}

	@Test
	void kohaPatronRequestsAnItemTheirOwnKohaHolds() {
		final var clusterRecordId = createClusterRecordWithOneKohaItem(BIBLIO_ID);

		final var placed = patronRequestApiClient.placePatronRequest(clusterRecordId,
			PATRON_ID, pickupLocationId, KOHA_HOST_LMS_CODE, HOME_BRANCH);

		assertThat(placed.body(), is(notNullValue()));
		final var requestId = placed.body().getId();

		await().atMost(30, SECONDS).until(() -> {
			final var status = patronRequestsFixture.findById(requestId).getStatus();
			log.debug("Request {} is {}", requestId, status);
			return HANDED_OFF_AS_LOCAL.equals(status);
		});

		final var request = patronRequestsFixture.findById(requestId);
		assertThat(request, hasActiveWorkflow(LOCAL_WORKFLOW));
		assertThat(request.getStatus(), is(HANDED_OFF_AS_LOCAL));

		// One hold, on the real item, for the real patron, at the branch they chose
		koha.verifyHoldPlaced("{\"patron_id\": 12, \"biblio_id\": 4001, \"item_id\": 55, \"pickup_library_id\": \"BRANCH-N\"}");
		koha.verifyHoldPlacedWithoutOverride();
		koha.verifyNoVirtualRecordsCreated();
	}

	@Test
	void kohaPatronRequestsACoTenantsItemOnTheSameKoha() {
		// A shared Koha: the item belongs to another library's branch, mapped to another
		// agency, but the patron, the item and the pickup branch are all on one server, so
		// the request is local: one real hold, which Koha transfers between its own branches
		agencyFixture.defineAgency("koha-local-south", "Koha South", kohaHostLms);
		referenceValueMappingFixture.defineLocationToAgencyMapping(KOHA_HOST_LMS_CODE, "BRANCH-S", "koha-local-south");
		koha.mockItemsPage(SOUTH_BIBLIO_ID, 1, item()
			.itemId(56L)
			.biblioId(Long.valueOf(SOUTH_BIBLIO_ID))
			.externalId("south-barcode")
			.homeLibraryId("BRANCH-S")
			.holdingLibraryId("BRANCH-S")
			.build());

		final var clusterRecordId = createClusterRecordWithOneKohaItem(SOUTH_BIBLIO_ID);

		final var placed = patronRequestApiClient.placePatronRequest(clusterRecordId,
			PATRON_ID, pickupLocationId, KOHA_HOST_LMS_CODE, HOME_BRANCH);

		assertThat(placed.body(), is(notNullValue()));
		final var requestId = placed.body().getId();

		await().atMost(30, SECONDS).until(() ->
			HANDED_OFF_AS_LOCAL.equals(patronRequestsFixture.findById(requestId).getStatus()));

		assertThat(patronRequestsFixture.findById(requestId), hasActiveWorkflow(LOCAL_WORKFLOW));
		koha.verifyHoldPlaced("{\"patron_id\": 12, \"biblio_id\": 4002, \"item_id\": 56, \"pickup_library_id\": \"BRANCH-N\"}");
		koha.verifyNoVirtualRecordsCreated();
	}

	private void mockKoha() {
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
		koha.mockItemsPage(BIBLIO_ID, 1, item().build());
		final var hold = KohaHoldResponse.builder()
			.holdId(Long.valueOf(HOLD_ID))
			.patronId(Long.valueOf(PATRON_ID))
			.biblioId(Long.valueOf(BIBLIO_ID))
			.itemId(Long.valueOf(ITEM_ID))
			.pickupLibraryId(HOME_BRANCH)
			.build();
		koha.mockPlaceHold(hold);
		koha.mockHold(hold);
	}

	private static KohaItem.KohaItemBuilder item() {
		return KohaItem.builder()
			.itemId(Long.valueOf(ITEM_ID))
			.biblioId(Long.valueOf(BIBLIO_ID))
			.externalId("real-barcode")
			.homeLibraryId(HOME_BRANCH)
			.holdingLibraryId(HOME_BRANCH)
			.itemTypeId("BK")
			.effectiveItemTypeId("BK")
			.notForLoanStatus(0)
			.effectiveNotForLoanStatus(0)
			.lostStatus(0)
			.withdrawn(0);
	}

	private UUID createClusterRecordWithOneKohaItem(String biblioId) {
		final var clusterRecordId = randomUUID();
		final var clusterRecord = clusterRecordFixture.createClusterRecord(clusterRecordId, clusterRecordId);
		bibRecordFixture.createBibRecord(clusterRecordId, kohaHostLms.getId(), biblioId, clusterRecord);
		return clusterRecordId;
	}
}
