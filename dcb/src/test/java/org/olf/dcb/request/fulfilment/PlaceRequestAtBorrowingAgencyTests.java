package org.olf.dcb.request.fulfilment;

import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.olf.dcb.core.model.PatronRequest.Status.CONFIRMED;
import static org.olf.dcb.core.model.PatronRequest.Status.ERROR;
import static org.olf.dcb.core.model.PatronRequest.Status.REQUEST_PLACED_AT_BORROWING_AGENCY;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;
import static org.olf.dcb.test.matchers.PatronRequestAuditMatchers.hasBriefDescription;
import static org.olf.dcb.test.matchers.PatronRequestAuditMatchers.hasFromStatus;
import static org.olf.dcb.test.matchers.PatronRequestAuditMatchers.hasToStatus;
import static org.olf.dcb.test.matchers.ThrowableMatchers.hasMessage;
import static org.olf.dcb.test.matchers.interaction.HttpResponseProblemMatchers.hasJsonResponseBodyProperty;
import static org.olf.dcb.test.matchers.interaction.HttpResponseProblemMatchers.hasResponseStatusCode;
import static org.olf.dcb.utils.PropertyAccessUtils.getValueOrNull;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockserver.client.MockServerClient;
import org.olf.dcb.core.interaction.sierra.SierraApiFixtureProvider;
import org.olf.dcb.core.interaction.sierra.SierraItem;
import org.olf.dcb.core.interaction.sierra.SierraItemsAPIFixture;
import org.olf.dcb.core.interaction.sierra.SierraPatronsAPIFixture;
import org.olf.dcb.core.model.DataAgency;
import org.olf.dcb.core.model.Location;
import org.olf.dcb.core.model.Patron;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.SupplierRequest;
import org.olf.dcb.request.CannotFindSelectedBibException;
import org.olf.dcb.request.resolution.CannotFindClusterRecordException;
import org.olf.dcb.request.workflow.PatronRequestWorkflowService;
import org.olf.dcb.request.workflow.PlacePatronRequestAtBorrowingAgencyStateTransition;
import org.olf.dcb.test.AgencyFixture;
import org.olf.dcb.test.BibRecordFixture;
import org.olf.dcb.test.ClusterRecordFixture;
import org.olf.dcb.test.HostLmsFixture;
import org.olf.dcb.test.LocationFixture;
import org.olf.dcb.test.PatronFixture;
import org.olf.dcb.test.PatronRequestsFixture;
import org.olf.dcb.test.ReferenceValueMappingFixture;
import org.olf.dcb.test.SupplierRequestsFixture;
import org.zalando.problem.ThrowableProblem;

import jakarta.inject.Inject;
import reactor.core.publisher.Mono;
import services.k_int.interaction.sierra.SierraCodeTuple;
import services.k_int.interaction.sierra.SierraTestUtils;
import services.k_int.interaction.sierra.bibs.BibPatch;
import services.k_int.interaction.sierra.holds.SierraPatronHold;
import services.k_int.test.mockserver.MockServerMicronautTest;

@MockServerMicronautTest
@TestInstance(PER_CLASS)
class PlaceRequestAtBorrowingAgencyTests {
	private static final String HOST_LMS_CODE = "borrowing-agency-service-tests";
	private static final String INVALID_HOLD_POLICY_HOST_LMS_CODE = "invalid-hold-policy";

	private static final String BORROWING_AGENCY_CODE = "borrowing-agency";
	private static final String SUPPLYING_AGENCY_CODE = "supplying-agency";

	@Inject
	private SierraApiFixtureProvider sierraApiFixtureProvider;

	@Inject
	private HostLmsFixture hostLmsFixture;
	@Inject
	private PatronRequestsFixture patronRequestsFixture;
	@Inject
	private PatronFixture patronFixture;
	@Inject
	private ClusterRecordFixture clusterRecordFixture;
	@Inject
	private BibRecordFixture bibRecordFixture;
	@Inject
	private AgencyFixture agencyFixture;
	@Inject
	private SupplierRequestsFixture supplierRequestsFixture;
	@Inject
	private ReferenceValueMappingFixture referenceValueMappingFixture;
	@Inject
	private LocationFixture locationFixture;

	private SierraPatronsAPIFixture sierraPatronsAPIFixture;
	private SierraItemsAPIFixture sierraItemsAPIFixture;

	@Inject
	private RequestWorkflowContextHelper requestWorkflowContextHelper;
	@Inject
	private PatronRequestWorkflowService patronRequestWorkflowService;

	@Inject
	private PlacePatronRequestAtBorrowingAgencyStateTransition placePatronRequestAtBorrowingAgencyStateTransition;

	private DataAgency supplyingAgency;

	@BeforeAll
	public void beforeAll(MockServerClient mockServerClient) {
		final String TOKEN = "test-token";
		final String BASE_URL = "https://borrowing-agency-service-tests.com";
		final String KEY = "borrowing-agency-service-key";
		final String SECRET = "borrowing-agency-service-secret";

		SierraTestUtils.mockFor(mockServerClient, BASE_URL)
			.setValidCredentials(KEY, SECRET, TOKEN, 60);

		hostLmsFixture.deleteAll();
		agencyFixture.deleteAll();

		var sierraHostLms = hostLmsFixture.createSierraHostLms(HOST_LMS_CODE, KEY,
			SECRET, BASE_URL, "title");

		hostLmsFixture.createSierraHostLms(INVALID_HOLD_POLICY_HOST_LMS_CODE, KEY,
			SECRET, BASE_URL, "invalid");

		agencyFixture.defineAgency(BORROWING_AGENCY_CODE,
			"Borrowing Agency", sierraHostLms);

		supplyingAgency = agencyFixture.defineAgency(SUPPLYING_AGENCY_CODE,
			"Supplying Agency", sierraHostLms);

		sierraPatronsAPIFixture = sierraApiFixtureProvider.patronsApiFor(mockServerClient);
		sierraItemsAPIFixture = sierraApiFixtureProvider.itemsApiFor(mockServerClient);

		final var sierraBibsAPIFixture = sierraApiFixtureProvider.bibsApiFor(mockServerClient);

		final var bibPatch = BibPatch.builder()
			.authors(List.of("Stafford Beer"))
			.titles(List.of("Brain of the Firm"))
			.build();

		sierraBibsAPIFixture.createPostBibsMock(bibPatch, 7916921);

		sierraItemsAPIFixture.successResponseForCreateItem(7916921,
			SUPPLYING_AGENCY_CODE, "9849123490", "7916922");

		sierraItemsAPIFixture.mockGetItemById("7916922",
			SierraItem.builder()
				.id("7916922")
				.statusCode("-")
				.build());

		sierraPatronsAPIFixture.addPatronGetExpectation("872321");
	}

	@BeforeEach
	void beforeEach() {
		patronRequestsFixture.deleteAll();
		patronFixture.deleteAllPatrons();
		clusterRecordFixture.deleteAll();
		locationFixture.deleteAll();
		referenceValueMappingFixture.deleteAll();

		referenceValueMappingFixture.defineLocationToAgencyMapping(HOST_LMS_CODE,
			BORROWING_AGENCY_CODE, BORROWING_AGENCY_CODE);

		referenceValueMappingFixture.defineLocationToAgencyMapping(
			INVALID_HOLD_POLICY_HOST_LMS_CODE, BORROWING_AGENCY_CODE,
			BORROWING_AGENCY_CODE);
	}

	@Test
	void shouldProgressConfirmedSuccessfully() {
		// Arrange
		final var clusterRecordId = randomUUID();
		final var bibRecordId = randomUUID();

		final var clusterRecord = clusterRecordFixture.createClusterRecord(
			clusterRecordId, bibRecordId);

		final var hostLms = hostLmsFixture.findByCode(HOST_LMS_CODE);
		final var sourceSystemId = hostLms.getId();

		bibRecordFixture.createBibRecord(bibRecordId, sourceSystemId, "798472", clusterRecord);

		final var localPatronId = "562967";

		final var patron = patronFixture.savePatron("home-library");

		final var hostLms1 = hostLmsFixture.findByCode(HOST_LMS_CODE);

		patronFixture.saveIdentity(patron, hostLms1, localPatronId, true, "-",
			localPatronId, null);

		final var agency = agencyFixture.findByCode(BORROWING_AGENCY_CODE);

		final var pickupLocation = locationFixture.createPickupLocation(agency);

		final var pickupLocationId = getValueOrNull(pickupLocation, Location::getId, UUID::toString);

		final var patronRequestId = randomUUID();

		var patronRequest = definePatronRequest(patronRequestId, clusterRecordId,
			patron, pickupLocationId, 1, null, null);

		patronRequestsFixture.savePatronRequest(patronRequest);

		createSupplierRequest(patronRequest, "76832", supplyingAgency);

		sierraPatronsAPIFixture.mockPlacePatronHoldRequest(localPatronId, "b", 7916921);

		// This one is for the borrower side hold - we now match a hold using the note instead of the itemid - so we have to fix up a hold with the
		// correct note containing the patronRequestId
		sierraPatronsAPIFixture.mockGetHoldsForPatronReturningSingleItemHold(localPatronId,
			"https://sandbox.iii.com/iii/sierra-api/v6/patrons/holds/864902",
			"Consortial Hold. tno="+patronRequest.getId(), localPatronId);

		sierraItemsAPIFixture.mockGetItemById(localPatronId,
			SierraItem.builder()
				.id(localPatronId)
				.barcode("6736255")
				.statusCode("-")
				.build());

		// Act
		final var pr = placeRequestAtBorrowingAgency(patronRequest);

		// Assert
		assertThat("Patron request should not be null", pr, is(notNullValue()));
		assertThat("Status code wasn't expected.", pr.getStatus(), is(REQUEST_PLACED_AT_BORROWING_AGENCY));
		assertThat("Local request id wasn't expected.", pr.getLocalRequestId(), is("864902"));
		assertThat("Local request status wasn't expected.", pr.getLocalRequestStatus(), is("CONFIRMED"));

		sierraPatronsAPIFixture.verifyPlaceHoldRequestMade(localPatronId, "b",
			7916921, pickupLocation.getCode(),
			"Consortial Hold. tno=%s \nFor UNKNOWN@null\n Pickup MISSING-PICKUP-LIB@null"
				.formatted(pr.getId()));
	}

	@Test
	void shouldProgressConfirmedSuccessfullyForReResolution() {
		// Arrange
		final var clusterRecordId = randomUUID();
		final var bibRecordId = randomUUID();

		final var clusterRecord = clusterRecordFixture.createClusterRecord(
			clusterRecordId, bibRecordId);

		final var hostLms = hostLmsFixture.findByCode(HOST_LMS_CODE);
		final var sourceSystemId = hostLms.getId();

		bibRecordFixture.createBibRecord(bibRecordId, sourceSystemId, "798472", clusterRecord);

		final var localPatronId = "562967";

		final var patron = patronFixture.savePatron("home-library");

		patronFixture.saveIdentity(patron, hostLms, localPatronId, true, "-",
			localPatronId, null);

		final var pickupLocation = locationFixture.createPickupLocation(
			agencyFixture.findByCode(BORROWING_AGENCY_CODE));

		final var pickupLocationId = getValueOrNull(pickupLocation, Location::getId,
			UUID::toString);

		final var patronRequestId = randomUUID();

		var patronRequest = definePatronRequest(patronRequestId, clusterRecordId,
			patron, pickupLocationId, 2, "864902", "243252");

		patronRequestsFixture.savePatronRequest(patronRequest);

		createSupplierRequest(patronRequest, "76832", supplyingAgency);

		sierraItemsAPIFixture.mockUpdateItem("243252");

		sierraItemsAPIFixture.mockGetItemById("243252",
			SierraItem.builder()
				.id("243252")
				.barcode("9849123490")
				.statusCode("-")
				.build());

		sierraPatronsAPIFixture.mockGetHoldById("864902",
			SierraPatronHold.builder()
				.id("864902")
				.recordType("i")
				.record("http://some-record/" + "243252")
				.status(SierraCodeTuple.builder()
					.code("0")
					.build())
				.build());

		referenceValueMappingFixture.defineMapping("DCB",
			"ItemType", "ebook", HOST_LMS_CODE, "ItemType", "007");

		// Act
		final var pr = placeRequestAtBorrowingAgency(patronRequest);

		// Assert
		assertThat("Patron request should not be null", pr, is(notNullValue()));
		assertThat("Status code wasn't expected.", pr.getStatus(), is(REQUEST_PLACED_AT_BORROWING_AGENCY));
		assertThat("Local request id wasn't expected.", pr.getLocalRequestId(), is("864902"));
		assertThat("Local request status wasn't expected.", pr.getLocalRequestStatus(), is("CONFIRMED"));

		sierraItemsAPIFixture.verifyUpdateItemRequestMade("243252");
	}

	@Test
	void shouldFailWhenPlacingRequestInSierraRespondsWithServerError() {
		// Arrange
		final var clusterRecordId = randomUUID();
		final var bibRecordId = randomUUID();

		final var clusterRecord = clusterRecordFixture.createClusterRecord(
			clusterRecordId, bibRecordId);

		final var hostLms = hostLmsFixture.findByCode(HOST_LMS_CODE);
		final var sourceSystemId = hostLms.getId();

		bibRecordFixture.createBibRecord(bibRecordId, sourceSystemId, "798472", clusterRecord);

		final var localPatronId = "972321";

		final var patronRequest = createPatronRequest(localPatronId,
			"home-library", clusterRecordId);

		createSupplierRequest(patronRequest, "647245", supplyingAgency);

		sierraPatronsAPIFixture.patronHoldRequestErrorResponse("972321", "b");

		// Act
		final var problem = assertThrows(ThrowableProblem.class,
			() -> placeRequestAtBorrowingAgency(patronRequest));

		// Assert
		final var expectedMessage = "Unexpected response from: POST /iii/sierra-api/v6/patrons/972321/holds/requests";

		assertThat(problem, allOf(
			hasMessage(expectedMessage),
			hasResponseStatusCode(500),
			hasJsonResponseBodyProperty("code", 109),
			hasJsonResponseBodyProperty("description", "Invalid configuration"),
			hasJsonResponseBodyProperty("httpStatus", 500),
			hasJsonResponseBodyProperty("name", "Internal server error"),
			hasJsonResponseBodyProperty("specificCode", 0)
		));

		final var fetchedPatronRequest = patronRequestsFixture.findById(patronRequest.getId());

		assertThat("Request should have error status afterwards",
			fetchedPatronRequest.getStatus(), is(ERROR));

		assertUnsuccessfulTransitionAudit(fetchedPatronRequest, expectedMessage);
	}

	@Test
	void shouldFailWhenPlacedRequestCannotBeFoundInSierra() {
		// Arrange
		final var clusterRecordId = randomUUID();
		final var bibRecordId = randomUUID();

		final var clusterRecord = clusterRecordFixture.createClusterRecord(
			clusterRecordId, bibRecordId);

		final var hostLms = hostLmsFixture.findByCode(HOST_LMS_CODE);
		final var sourceSystemId = hostLms.getId();

		bibRecordFixture.createBibRecord(bibRecordId, sourceSystemId, "798472", clusterRecord);

		final var localPatronId = "562967";
		final var homeLibraryCode = "972321";

		final var patronRequest = createPatronRequest(localPatronId,
			homeLibraryCode, clusterRecordId);

		createSupplierRequest(patronRequest, "35365", supplyingAgency);

		sierraPatronsAPIFixture.mockPlacePatronHoldRequest(localPatronId, "b", 7916921);

		sierraPatronsAPIFixture.notFoundWhenGettingPatronRequests(localPatronId);

		// Act
		final var exception = assertThrows(RuntimeException.class,
			() -> placeRequestAtBorrowingAgency(patronRequest));

		// Assert
		String expectedNote="No holds to process for local patron id: " + localPatronId;

		assertThat(exception.getMessage(), startsWith(expectedNote));

		final var fetchedPatronRequest = patronRequestsFixture.findById(patronRequest.getId());

		assertThat("Request should have error status afterwards",
			fetchedPatronRequest.getStatus(), is(ERROR));


		assertThat("Request should have error message afterwards", fetchedPatronRequest.getErrorMessage(), is(expectedNote));

		assertUnsuccessfulTransitionAudit(fetchedPatronRequest, expectedNote);
	}

	@Test
	void shouldFailWhenSierraHostLmsHasInvalidHoldPolicyConfiguration() {
		// Arrange
		final var clusterRecordId = randomUUID();
		final var bibRecordId = randomUUID();

		final var clusterRecord = clusterRecordFixture.createClusterRecord(
			clusterRecordId, bibRecordId);

		final var invalidHoldPolicyHostLms = hostLmsFixture.findByCode(INVALID_HOLD_POLICY_HOST_LMS_CODE);

		final var sourceSystemId = invalidHoldPolicyHostLms.getId();

		bibRecordFixture.createBibRecord(bibRecordId, sourceSystemId, "798472", clusterRecord);

		final var localPatronId = "562967";

		final var patron = patronFixture.savePatron("872321");

		patronFixture.saveIdentity(patron, invalidHoldPolicyHostLms, localPatronId, true, "-",
			localPatronId, null);

		final var patronRequestId = randomUUID();

		final var pickupLocationId = createPickupLocation(
			agencyFixture.findByCode(BORROWING_AGENCY_CODE));

		var patronRequest = definePatronRequest(patronRequestId, clusterRecordId,
			patron, pickupLocationId, 1, "864902", "243252");

		patronRequestsFixture.savePatronRequest(patronRequest);

		createSupplierRequest(patronRequest, "76832", supplyingAgency);

		// Act
		final var exception = assertThrows(RuntimeException.class,
			() -> placeRequestAtBorrowingAgency(patronRequest));

		// Assert
		final var expectedErrorMessage = "Invalid hold policy for Host LMS \""
			+ INVALID_HOLD_POLICY_HOST_LMS_CODE + "\"";

		assertThat("Should have invalid hold policy message",
			exception.getMessage(), is(expectedErrorMessage));

		final var fetchedPatronRequest = patronRequestsFixture.findById(patronRequest.getId());

		assertThat("Request should have error status afterwards",
			fetchedPatronRequest.getStatus(), is(ERROR));

		assertThat("Request should have error message afterwards",
			fetchedPatronRequest.getErrorMessage(), is(expectedErrorMessage));

		assertUnsuccessfulTransitionAudit(fetchedPatronRequest, expectedErrorMessage);
	}

	@Test
	void shouldFailWhenSelectedBibCannotBeFound() {
		// Arrange
		final var clusterRecordId = randomUUID();
		final var bibRecordId = randomUUID();

		clusterRecordFixture.createClusterRecord(clusterRecordId, bibRecordId);

		final var localPatronId = "562967";
		final var homeLibraryCode = "972321";

		final var patronRequest = createPatronRequest(localPatronId,
			homeLibraryCode, clusterRecordId);

		createSupplierRequest(patronRequest, "798472", supplyingAgency);

		// Act
		final var exception = assertThrows(CannotFindSelectedBibException.class,
			() -> placeRequestAtBorrowingAgency(patronRequest));

		// Assert
		final var expectedErrorMessage = "Unable to locate selected bib " + bibRecordId
			+ " for cluster " + clusterRecordId;

		assertThat(exception, hasMessage(expectedErrorMessage));

		final var fetchedPatronRequest = patronRequestsFixture.findById(patronRequest.getId());

		assertThat("Request should have error status afterwards",
			fetchedPatronRequest.getStatus(), is(ERROR));

		assertThat("Request should have error message afterwards",
			fetchedPatronRequest.getErrorMessage(), is(expectedErrorMessage));

		assertUnsuccessfulTransitionAudit(fetchedPatronRequest, expectedErrorMessage);
	}

	@Test
	void shouldFailWhenLendingAgencyIsInvalid() {
		// Arrange
		final var clusterRecordId = randomUUID();
		final var bibRecordId = randomUUID();

		final var clusterRecord = clusterRecordFixture.createClusterRecord(
			clusterRecordId, bibRecordId);

		final var hostLms = hostLmsFixture.findByCode(HOST_LMS_CODE);
		final var sourceSystemId = hostLms.getId();

		bibRecordFixture.createBibRecord(bibRecordId, sourceSystemId, "798472", clusterRecord);

		final var localPatronId = "562967";

		final var patronRequest = createPatronRequest(localPatronId,
			"home-library", clusterRecordId);

		// define an agency that does not exist
		final var invalidAgency = DataAgency.builder().id(randomUUID()).code("INVALID_AGENCY").build();

		createSupplierRequest(patronRequest, "76832", invalidAgency);

		// Act
		final var exception = assertThrows(RuntimeException.class,
			() -> placeRequestAtBorrowingAgency(patronRequest));

		// Assert
		final var expectedErrorMessage = "Failed to find valid supplying agency for resolved agency UUID: "
			+ invalidAgency.getId();

		assertThat("Should have an invalid supplying lending agency message",
			exception.getMessage(), is(expectedErrorMessage));

		final var fetchedPatronRequest = patronRequestsFixture.findById(patronRequest.getId());

		assertThat("Request should have error status afterwards",
			fetchedPatronRequest.getStatus(), is(ERROR));

		assertThat("Request should have error message afterwards",
			fetchedPatronRequest.getErrorMessage(), is(expectedErrorMessage));

		assertUnsuccessfulTransitionAudit(fetchedPatronRequest, expectedErrorMessage);
	}

	@Test
	void shouldFailWhenClusterRecordCannotBeFound() {
		// Arrange
		final var clusterRecordId = randomUUID();

		final var localPatronId = "562967";

		final var patronRequest = createPatronRequest(localPatronId,
			"home-library", clusterRecordId);

		createSupplierRequest(patronRequest, "76832", supplyingAgency);

		// Act
		final var exception = assertThrows(CannotFindClusterRecordException.class,
			() -> placeRequestAtBorrowingAgency(patronRequest));

		// Assert
		final var expectedErrorMessage = "Cannot find cluster record for: " + clusterRecordId;

		assertThat(exception, hasMessage(expectedErrorMessage));

		final var fetchedPatronRequest = patronRequestsFixture.findById(patronRequest.getId());

		assertThat("Request should have error status afterwards",
			fetchedPatronRequest.getStatus(), is(ERROR));

		assertThat("Request should have error message afterwards",
			fetchedPatronRequest.getErrorMessage(), is(expectedErrorMessage));

		assertUnsuccessfulTransitionAudit(fetchedPatronRequest, expectedErrorMessage);
	}

	private void assertUnsuccessfulTransitionAudit(PatronRequest patronRequest, String description) {
		final var fetchedAudit = patronRequestsFixture.findAuditEntries(patronRequest);

		assertThat(fetchedAudit, containsInAnyOrder(
			allOf(
				hasBriefDescription(description),
				hasFromStatus(CONFIRMED),
				hasToStatus(ERROR)
			)
		));
	}

	private PatronRequest placeRequestAtBorrowingAgency(PatronRequest patronRequest) {
		return singleValueFrom(requestWorkflowContextHelper.fromPatronRequest(patronRequest)
			.flatMap(ctx -> {
				if (!placePatronRequestAtBorrowingAgencyStateTransition.isApplicableFor(ctx)) {
					return Mono.error(new RuntimeException("Place request at borrowing agency is not applicable for request"));
				}

				return Mono.just(ctx.getPatronRequest())
					.flatMap(patronRequestWorkflowService.attemptTransitionWithErrorTransformer(
						placePatronRequestAtBorrowingAgencyStateTransition, ctx));
			})
			.thenReturn(patronRequest));
	}

	private String createPickupLocation(DataAgency agency) {
		final var pickupLocation = locationFixture.createPickupLocation(agency);

		return getValueOrNull(pickupLocation, Location::getId, UUID::toString);
	}

	private PatronRequest createPatronRequest(String localPatronId,
		String homeLibraryCode, UUID clusterRecordId) {

		final var patron = patronFixture.savePatron(homeLibraryCode);

		final var hostLms = hostLmsFixture.findByCode(HOST_LMS_CODE);

		patronFixture.saveIdentity(patron, hostLms, localPatronId, true, "-",
			localPatronId, null);

		final var agency = agencyFixture.findByCode(BORROWING_AGENCY_CODE);

		final var pickupLocationId = createPickupLocation(agency);

		final var patronRequestId = randomUUID();

		var patronRequest = definePatronRequest(patronRequestId, clusterRecordId,
			patron, pickupLocationId, 1, null, null);

		patronRequestsFixture.savePatronRequest(patronRequest);

		return patronRequest;
	}

	private static PatronRequest definePatronRequest(UUID patronRequestId, UUID clusterRecordId,
		Patron patron, String pickupLocationId, Integer resolutionCount,
		String localRequestId, String localItemId) {

		return PatronRequest.builder()
			.id(patronRequestId)
			.patron(patron)
			.bibClusterId(clusterRecordId)
			.status(CONFIRMED)
			.pickupLocationCode(pickupLocationId)
			.localRequestId(localRequestId)
			.localItemId(localItemId)
			.resolutionCount(resolutionCount)
			.build();
	}

	private void createSupplierRequest(PatronRequest patronRequest,
		String localBibId, DataAgency supplyingAgency) {

		supplierRequestsFixture.saveSupplierRequest(SupplierRequest
			.builder()
			.id(randomUUID())
			.patronRequest(patronRequest)
			.localBibId(localBibId)
			.localItemId("localItemId")
			.canonicalItemType("ebook")
			.localItemLocationCode(SUPPLYING_AGENCY_CODE)
			.localItemBarcode("9849123490")
			.hostLmsCode(HOST_LMS_CODE)
			.resolvedAgency(supplyingAgency)
			.isActive(true)
			.build());
	}
}
