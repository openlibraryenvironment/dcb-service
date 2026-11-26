package org.olf.dcb.api;

import static org.olf.dcb.core.model.PatronRequest.Status.REQUEST_PLACED_AT_BORROWING_AGENCY;
import static io.micronaut.http.HttpStatus.OK;
import static java.lang.Integer.parseInt;
import static java.util.UUID.randomUUID;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.olf.dcb.core.model.FunctionalSettingType.RE_RESOLUTION;
import static org.olf.dcb.core.model.FunctionalSettingType.RE_RESOLVE_SUPPLIER_REFUSALS;
import static org.olf.dcb.core.model.PatronRequest.Status.ERROR;
import static org.olf.dcb.core.model.PatronRequest.Status.NOT_SUPPLIED_CURRENT_SUPPLIER;
import static org.olf.dcb.core.model.PatronRequest.Status.NO_ITEMS_SELECTABLE_AT_ANY_AGENCY;
import static org.olf.dcb.request.fulfilment.PatronRequestService.ROLLBACK_ACTIONED;
import static org.olf.dcb.request.fulfilment.SupplierPlacementGate.SUPPLIER_CANNOT_SUPPLY;
import static org.olf.dcb.request.fulfilment.SupplierPlacementGate.SUPPLIER_REFUSED;
import static org.olf.dcb.request.workflow.ResolveNextSupplierTransition.NO_OTHER_SUPPLIER;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

import org.awaitility.core.ConditionTimeoutException;

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
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.PatronRequestAudit;
import org.olf.dcb.test.AgencyFixture;
import org.olf.dcb.test.BibRecordFixture;
import org.olf.dcb.test.ClusterRecordFixture;
import org.olf.dcb.test.ConsortiumFixture;
import org.olf.dcb.test.HostLmsFixture;
import org.olf.dcb.test.LocationFixture;
import org.olf.dcb.test.PatronFixture;
import org.olf.dcb.test.PatronRequestsFixture;
import org.olf.dcb.test.ReferenceValueMappingFixture;
import org.olf.dcb.test.SupplierRequestsFixture;

import jakarta.inject.Inject;
import services.k_int.interaction.sierra.FixedField;
import services.k_int.interaction.sierra.SierraCodeTuple;
import services.k_int.interaction.sierra.SierraTestUtils;
import services.k_int.interaction.sierra.bibs.BibPatch;
import services.k_int.interaction.sierra.holds.SierraPatronHold;
import services.k_int.interaction.sierra.patrons.SierraPatronRecord;
import services.k_int.test.mockserver.MockServerMicronautTest;

/**
 * A supplier whose own configuration refuses a request: Sierra's XCirc refusing a hold because of
 * the virtual patron's record, the case seen when a library's Patron Blocks table stops DCB's patrons.
 */
@MockServerMicronautTest
@TestInstance(PER_CLASS)
class SupplierRefusalApiTests {
	private static final String SUPPLYING_HOST_LMS_CODE = "refusal-api-supplier";
	private static final String SUPPLYING_BASE_URL = "https://supplier-refusal-api-tests.com";
	private static final String BORROWING_HOST_LMS_CODE = "refusal-api-borrower";
	private static final String BORROWING_BASE_URL = "https://borrower-refusal-api-tests.com";
	private static final String SUPPLYING_AGENCY_CODE = "refusal-supplying-agency";
	private static final String BORROWING_AGENCY_CODE = "refusal-borrowing-agency";
	private static final String SUPPLYING_BIB_ID = "8810001";
	private static final String SUPPLYING_ITEM_ID = "8810002";
	private static final String SUPPLYING_ITEM_BARCODE = "8810003";
	private static final String SUPPLYING_LOCATION_CODE = "rf6";
	private static final String BORROWER_HOME_LOCATION_CODE = "rfbor";
	private static final String PICKUP_LOCATION_CODE = "RF123";
	private static final String PICKUP_LOCATION_ID = "5b3e1a0c-6a2d-4c55-9a43-0c4bd1e5f101";
	private static final String VIRTUAL_PATRON_TYPE = "15";
	// Locally the workflow's last save lands about 150ms after ERROR is visible
	private static final Duration NO_FURTHER_SAVES_FOR = Duration.ofSeconds(2);

	@Inject
	private SierraApiFixtureProvider sierraApiFixtureProvider;
	@Inject
	private PatronRequestsFixture patronRequestsFixture;
	@Inject
	private SupplierRequestsFixture supplierRequestsFixture;
	@Inject
	private PatronFixture patronFixture;
	@Inject
	private HostLmsFixture hostLmsFixture;
	@Inject
	private ClusterRecordFixture clusterRecordFixture;
	@Inject
	private BibRecordFixture bibRecordFixture;
	@Inject
	private ReferenceValueMappingFixture referenceValueMappingFixture;
	@Inject
	private AgencyFixture agencyFixture;
	@Inject
	private LocationFixture locationFixture;
	@Inject
	private ConsortiumFixture consortiumFixture;
	@Inject
	private PatronRequestApiClient patronRequestApiClient;
	@Inject
	private AdminApiClient adminApiClient;

	private SierraPatronsAPIFixture sierraPatronsAPIFixture;
	private SierraItemsAPIFixture sierraItemsAPIFixture;
	private SierraBibsAPIFixture sierraBibsAPIFixture;

	@BeforeAll
	void beforeAll(MockServerClient mockServerClient) {
		final String TOKEN = "refusal-token";
		final String KEY = "refusal-key";
		final String SECRET = "refusal-secret";

		SierraTestUtils.mockFor(mockServerClient, BORROWING_BASE_URL).setValidCredentials(KEY, SECRET, TOKEN, 3600);
		SierraTestUtils.mockFor(mockServerClient, SUPPLYING_BASE_URL).setValidCredentials(KEY, SECRET, TOKEN, 3600);

		locationFixture.deleteAll();
		agencyFixture.deleteAll();
		hostLmsFixture.deleteAll();

		final var supplyingHostLms = hostLmsFixture.createSierraHostLms(SUPPLYING_HOST_LMS_CODE, KEY, SECRET, SUPPLYING_BASE_URL);
		final var borrowingHostLms = hostLmsFixture.createSierraHostLms(BORROWING_HOST_LMS_CODE, KEY, SECRET, BORROWING_BASE_URL);

		sierraPatronsAPIFixture = sierraApiFixtureProvider.patrons(mockServerClient);
		sierraItemsAPIFixture = sierraApiFixtureProvider.items(mockServerClient);
		sierraBibsAPIFixture = sierraApiFixtureProvider.bibs(mockServerClient);

		sierraItemsAPIFixture.itemsForBibId(SUPPLYING_BIB_ID, List.of(SierraItem.builder()
			.id(SUPPLYING_ITEM_ID)
			.statusCode("-")
			.callNumber("BL221 .C48")
			.locationCode(SUPPLYING_LOCATION_CODE)
			.locationName("Refusal Stacks")
			.barcode(SUPPLYING_ITEM_BARCODE)
			.itemType("999")
			.fixedFields(Map.of(61, FixedField.builder().value("999").build()))
			.holdCount(0)
			.build()));

		final var borrowingAgency = agencyFixture.defineAgency(BORROWING_AGENCY_CODE, "Refusal Borrowing Agency", borrowingHostLms);
		agencyFixture.defineAgency(SUPPLYING_AGENCY_CODE, "Refusal Supplying Agency", supplyingHostLms);

		locationFixture.createPickupLocation(UUID.fromString(PICKUP_LOCATION_ID),
			PICKUP_LOCATION_CODE, PICKUP_LOCATION_CODE, borrowingAgency);
	}

	@BeforeEach
	void beforeEach() {
		patronRequestsFixture.deleteAll();
		patronFixture.deleteAllPatrons();
		clusterRecordFixture.deleteAll();
		referenceValueMappingFixture.deleteAll();
		consortiumFixture.deleteAll();

		consortiumFixture.enableAllSettings();

		referenceValueMappingFixture.defineLocationToAgencyMapping(
			SUPPLYING_HOST_LMS_CODE, SUPPLYING_LOCATION_CODE, SUPPLYING_AGENCY_CODE);
		referenceValueMappingFixture.defineLocationToAgencyMapping(
			BORROWING_HOST_LMS_CODE, BORROWER_HOME_LOCATION_CODE, BORROWING_AGENCY_CODE);
		referenceValueMappingFixture.defineLocationToAgencyMapping(
			SUPPLYING_HOST_LMS_CODE, PICKUP_LOCATION_CODE, BORROWING_AGENCY_CODE);
		referenceValueMappingFixture.defineLocalToCanonicalItemTypeRangeMapping(
			SUPPLYING_HOST_LMS_CODE, 999, 999, "loanable-item");
		referenceValueMappingFixture.defineNumericPatronTypeRangeMapping(
			BORROWING_HOST_LMS_CODE, 10, 20, "DCB", "15");
		referenceValueMappingFixture.definePatronTypeMapping("DCB", "15", SUPPLYING_HOST_LMS_CODE, VIRTUAL_PATRON_TYPE);
		referenceValueMappingFixture.defineNumericPatronTypeRangeMapping(
			SUPPLYING_HOST_LMS_CODE, 15, 15, "DCB", "UNDERGRAD");
	}

	@AfterAll
	void afterAll() {
		patronRequestApiClient.removeTokenFromValidTokens();
	}

	@Test
	void aRequestNoOtherLibraryCanSupplyAfterARefusalWaitsInErrorForTheLibraryToFixItsConfiguration() {
		final var patrons = new Patrons("8810101", "8810201");
		patrons.mockRefusingSupplier();

		final var requestId = placeRequest(patrons);

		final var errored = awaitStatus(requestId, ERROR);

		// The copy exists and a configuration change at the supplier would let it be lent
		assertThat(errored.getStatus(), is(not(NO_ITEMS_SELECTABLE_AT_ANY_AGENCY)));
		assertThat(errored.getErrorMessage(), containsString(
			"refusal-api-supplier's Sierra refused the hold because of DCB's patron record there (P TYPE 15)"));
		// Rolling back returns it to re-resolution, which is what tries the supplier again
		assertThat(errored.getPreviousStatus(), is(NOT_SUPPLIED_CURRENT_SUPPLIER));
		assertThat(briefDescriptions(errored), hasItem(SUPPLIER_REFUSED));
		assertThat(briefDescriptions(errored), hasItem(NO_OTHER_SUPPLIER));
	}

	@Test
	void rollingBackAfterTheLibraryFixesItsConfigurationPlacesTheRequestThere() {
		final var patrons = new Patrons("8810102", "8810202");
		patrons.mockRefusingSupplier();

		final var requestId = placeRequest(patrons);
		awaitStatus(requestId, ERROR);

		patrons.supplierAcceptsHoldsAgain(requestId);

		rollBack(requestId);

		awaitStatus(requestId, REQUEST_PLACED_AT_BORROWING_AGENCY);

		final var placed = adminApiClient.getPatronRequestViaAdminApi(requestId);

		final var supplierRequest = placed.getSupplierRequests().stream()
			.filter(request -> "PLACED".equals(request.getStatus()))
			.findFirst().orElseThrow();

		assertThat(supplierRequest.getHostLmsCode(), is(SUPPLYING_HOST_LMS_CODE));
		assertThat(briefDescriptions(patronRequestsFixture.findById(requestId)), hasItem(ROLLBACK_ACTIONED));
	}

	@Test
	void rollingBackBeforeTheLibraryFixesItsConfigurationTriesItOnceMoreThenWaitsInErrorAgain() {
		final var patrons = new Patrons("8810103", "8810203");
		patrons.mockRefusingSupplier();

		final var requestId = placeRequest(patrons);
		awaitStatus(requestId, ERROR);

		rollBack(requestId);

		// Twice and no more: a refusal after the rollback is not retried until staff roll back again
		awaitState(requestId, request -> request.getStatus() == ERROR && refusals(request) == 2,
			"ERROR after a second refusal");
	}

	@Test
	void aRefusalEndsInErrorWithItsReasonWhenTheConsortiumDoesNotReResolveRefusals() {
		consortiumFixture.deleteAll();
		consortiumFixture.enableAllSettingsExcept(List.of(RE_RESOLVE_SUPPLIER_REFUSALS));

		final var patrons = new Patrons("8810104", "8810204");
		patrons.mockRefusingSupplier();

		final var errored = awaitStatus(placeRequest(patrons), ERROR);

		assertThat(errored.getErrorMessage(), startsWith(
			"refusal-api-supplier's Sierra refused the hold because of DCB's patron record there (P TYPE 15)"));
		assertThat(refusals(errored), is(0L));
	}

	@Test
	void aRefusalEndsInErrorWithItsReasonWhenNothingWouldLookForAnotherSupplier() {
		consortiumFixture.deleteAll();
		consortiumFixture.enableAllSettingsExcept(List.of(RE_RESOLUTION));

		final var patrons = new Patrons("8810105", "8810205");
		patrons.mockRefusingSupplier();

		final var errored = awaitStatus(placeRequest(patrons), ERROR);

		assertThat(errored.getErrorMessage(), startsWith(
			"refusal-api-supplier's Sierra refused the hold because of DCB's patron record there (P TYPE 15)"));
		assertThat(refusals(errored), is(0L));
	}

	@Test
	void aChosenCopyThatIsRefusedEndsInErrorWithTheRefusalsReason() {
		final var patrons = new Patrons("8810106", "8810206");
		patrons.mockRefusingSupplier();

		final var clusterRecordId = createTitle();

		final var placedResponse = patronRequestApiClient.placePatronRequest(
			PatronRequestApiClient.PlacePatronRequestCommand.builder()
				.requestor(PatronRequestApiClient.Requestor.builder()
					.localId(patrons.borrowerLocalId)
					.localSystemCode(BORROWING_HOST_LMS_CODE)
					.homeLibraryCode(BORROWER_HOME_LOCATION_CODE)
					.build())
				.citation(PatronRequestApiClient.Citation.builder().bibClusterId(clusterRecordId).build())
				.pickupLocation(PatronRequestApiClient.PickupLocation.builder().code(PICKUP_LOCATION_ID).build())
				.item(PatronRequestApiClient.Item.builder()
					.localId(SUPPLYING_ITEM_ID)
					.localSystemCode(SUPPLYING_HOST_LMS_CODE)
					.agencyCode(SUPPLYING_AGENCY_CODE)
					.build())
				.build());

		assertThat(placedResponse.getStatus(), is(OK));

		final var errored = awaitStatus(placedResponse.body().getId(), ERROR);

		assertThat(errored.getErrorMessage(), startsWith(
			"refusal-api-supplier's Sierra refused the hold because of DCB's patron record there (P TYPE 15)"));
		assertThat(refusals(errored), is(0L));
	}

	@Test
	void aSupplierFailureThatIsNotARefusalStillEndsInError() {
		final var patrons = new Patrons("8810107", "8810207");
		patrons.mockFoundPatrons();
		sierraPatronsAPIFixture.patronHoldRequestErrorResponse(patrons.virtualPatronLocalId, "i");

		final var errored = awaitStatus(placeRequest(patrons), ERROR);

		assertThat(errored.getErrorMessage(), startsWith("Unable to place SUPPLIER hold request"));
		assertThat(refusals(errored), is(0L));
	}

	private UUID placeRequest(Patrons patrons) {
		final var placedResponse = patronRequestApiClient.placePatronRequest(createTitle(),
			patrons.borrowerLocalId, PICKUP_LOCATION_ID, BORROWING_HOST_LMS_CODE, BORROWER_HOME_LOCATION_CODE);

		assertThat(placedResponse.getStatus(), is(OK));

		return placedResponse.body().getId();
	}

	// Tracking picks a rolled-back request up at its next poll; asking it to check now is the same step, sooner
	private void rollBack(UUID requestId) {
		awaitNoFurtherSaves(requestId);

		assertThat(patronRequestApiClient.rollbackPatronRequest(requestId).getStatus(), is(OK));
		assertThat(patronRequestApiClient.updatePatronRequest(requestId).getStatus(), is(OK));
	}

	private UUID createTitle() {
		final var clusterRecordId = randomUUID();
		final var clusterRecord = clusterRecordFixture.createClusterRecord(clusterRecordId, clusterRecordId);

		bibRecordFixture.createBibRecord(clusterRecordId,
			hostLmsFixture.findByCode(SUPPLYING_HOST_LMS_CODE).getId(), SUPPLYING_BIB_ID, clusterRecord);

		return clusterRecordId;
	}

	private PatronRequest awaitStatus(UUID requestId, PatronRequest.Status status) {
		return awaitState(requestId, request -> request.getStatus() == status, status.name());
	}

	// The workflow that put the request in ERROR saves its own copy once more after ERROR is visible, overwriting a
	// rollback made in between. Nothing but dateUpdated shows that save, so wait until it stops changing
	private void awaitNoFurtherSaves(UUID requestId) {
		final var lastSaved = new AtomicReference<Instant>();

		awaitState(requestId, request -> request.getDateUpdated() != null
				&& request.getDateUpdated().equals(lastSaved.getAndSet(request.getDateUpdated())),
			"no further saves for " + NO_FURTHER_SAVES_FOR, NO_FURTHER_SAVES_FOR);
	}

	private PatronRequest awaitState(UUID requestId, Predicate<PatronRequest> reached, String expected) {
		return awaitState(requestId, reached, expected, Duration.ofMillis(100));
	}

	// A shared CI runner is slower than a workstation; and on a timeout, say where the request stopped
	private PatronRequest awaitState(UUID requestId, Predicate<PatronRequest> reached, String expected,
		Duration pollInterval) {

		try {
			return await().atMost(60, SECONDS).pollInterval(pollInterval)
				.until(() -> patronRequestsFixture.findById(requestId), reached);
		}
		catch (ConditionTimeoutException timeout) {
			throw new AssertionError("Request " + requestId + " did not reach " + expected
				+ patronRequestsFixture.describe(requestId), timeout);
		}
	}

	private List<String> briefDescriptions(PatronRequest request) {
		return patronRequestsFixture.findAuditEntries(request).stream()
			.map(PatronRequestAudit::getBriefDescription)
			.toList();
	}

	private long refusals(PatronRequest request) {
		return briefDescriptions(request).stream().filter(SUPPLIER_CANNOT_SUPPLY::equals).count();
	}

	/** A borrower at the borrowing library, and the virtual patron DCB finds for them at the supplier. */
	private class Patrons {
		private final String borrowerLocalId;
		private final String virtualPatronLocalId;

		Patrons(String borrowerLocalId, String virtualPatronLocalId) {
			this.borrowerLocalId = borrowerLocalId;
			this.virtualPatronLocalId = virtualPatronLocalId;
		}

		void mockFoundPatrons() {
			sierraPatronsAPIFixture.mockGetPatronById(borrowerLocalId, SierraPatronRecord.builder()
				.id(parseInt(borrowerLocalId))
				.patronType(15)
				.homeLibraryCode(BORROWER_HOME_LOCATION_CODE)
				.barcodes(List.of("b" + borrowerLocalId))
				.build());

			sierraPatronsAPIFixture.patronsQueryFoundResponse(
				"%s@%s".formatted(borrowerLocalId, BORROWING_AGENCY_CODE), virtualPatronLocalId);

			sierraPatronsAPIFixture.mockGetPatronById(virtualPatronLocalId, SierraPatronRecord.builder()
				.id(parseInt(virtualPatronLocalId))
				.patronType(parseInt(VIRTUAL_PATRON_TYPE))
				.homeLibraryCode("rfhome")
				.build());
		}

		void mockRefusingSupplier() {
			mockFoundPatrons();
			sierraPatronsAPIFixture.problemWithLibraryRecordResponse(virtualPatronLocalId, "i");
			sierraPatronsAPIFixture.mockGetHoldsForPatronReturningCount(virtualPatronLocalId, 25);
		}

		/** What a successful placement at both libraries reads back from Sierra. */
		void supplierAcceptsHoldsAgain(UUID requestId) {
			final var supplierHoldId = virtualPatronLocalId + "1";
			final var borrowerHoldId = borrowerLocalId + "1";
			final var virtualBibId = Integer.parseInt(borrowerLocalId + "2");
			final var virtualItemId = borrowerLocalId + "3";

			sierraPatronsAPIFixture.acceptHoldRequestAfterProblemWithLibraryRecord(virtualPatronLocalId, "i");

			sierraPatronsAPIFixture.mockGetHoldsForPatronReturningSingleItemHold(virtualPatronLocalId,
				"https://sandbox.iii.com/iii/sierra-api/v6/patrons/holds/" + supplierHoldId,
				"Consortial Hold. tno=" + requestId, SUPPLYING_ITEM_ID);

			sierraItemsAPIFixture.mockGetItemById(SUPPLYING_ITEM_ID, SierraItem.builder()
				.id(SUPPLYING_ITEM_ID)
				.barcode(SUPPLYING_ITEM_BARCODE)
				.statusCode("-")
				.itemType("999")
				.fixedFields(Map.of(61, FixedField.builder().value("999").build()))
				.build());

			sierraPatronsAPIFixture.mockGetHoldById(supplierHoldId, SierraPatronHold.builder()
				.id(supplierHoldId)
				.recordType("i")
				.record("http://some-record/" + SUPPLYING_ITEM_ID)
				.status(SierraCodeTuple.builder().code("0").build())
				.build());

			sierraBibsAPIFixture.createPostBibsMock(BibPatch.builder()
				.fixedFields(Map.of(31, FixedField.builder().label("suppress").value("n").build()))
				.authors(List.of("Stafford Beer"))
				.titles(List.of("Brain of the Firm"))
				.build(), virtualBibId);

			sierraItemsAPIFixture.successResponseForCreateItem(virtualBibId,
				SUPPLYING_AGENCY_CODE, SUPPLYING_ITEM_BARCODE, virtualItemId);

			sierraItemsAPIFixture.mockGetItemById(virtualItemId, SierraItem.builder()
				.id(virtualItemId)
				.statusCode("-")
				.itemType("999")
				.fixedFields(Map.of(61, FixedField.builder().value("999").build()))
				.build());

			sierraPatronsAPIFixture.mockPlacePatronHoldRequest(borrowerLocalId, "i", null);

			sierraPatronsAPIFixture.mockGetHoldsForPatronReturningSingleItemHold(borrowerLocalId,
				"https://sandbox.iii.com/iii/sierra-api/v6/patrons/holds/" + borrowerHoldId,
				"Consortial Hold. tno=" + requestId, virtualItemId);
		}
	}
}
