package org.olf.dcb.request.fulfilment;

import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.olf.dcb.core.model.PatronRequest.Status.ERROR;
import static org.olf.dcb.core.model.PatronRequest.Status.REQUEST_PLACED_AT_SUPPLYING_AGENCY;
import static org.olf.dcb.test.matchers.ThrowableMatchers.hasMessage;
import static org.olf.dcb.test.matchers.interaction.UnexpectedResponseProblemMatchers.hasJsonResponseBodyProperty;
import static org.olf.dcb.test.matchers.interaction.UnexpectedResponseProblemMatchers.hasResponseStatusCodeParameter;

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
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.PatronRequest.Status;
import org.olf.dcb.request.CannotFindSelectedBibException;
import org.olf.dcb.request.resolution.CannotFindClusterRecordException;
import org.olf.dcb.request.workflow.PlacePatronRequestAtBorrowingAgencyStateTransition;
import org.olf.dcb.test.AgencyFixture;
import org.olf.dcb.test.BibRecordFixture;
import org.olf.dcb.test.ClusterRecordFixture;
import org.olf.dcb.test.HostLmsFixture;
import org.olf.dcb.test.PatronFixture;
import org.olf.dcb.test.PatronRequestsFixture;
import org.olf.dcb.test.ReferenceValueMappingFixture;
import org.olf.dcb.test.SupplierRequestsFixture;
import org.zalando.problem.ThrowableProblem;

import jakarta.inject.Inject;
import services.k_int.interaction.sierra.SierraTestUtils;
import services.k_int.interaction.sierra.bibs.BibPatch;
import services.k_int.test.mockserver.MockServerMicronautTest;

@MockServerMicronautTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PlaceRequestAtBorrowingAgencyTests {
	private static final String HOST_LMS_CODE = "borrowing-agency-service-tests";
	private static final String INVALID_HOLD_POLICY_HOST_LMS_CODE = "invalid-hold-policy";

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

	private SierraPatronsAPIFixture sierraPatronsAPIFixture;
	private SierraItemsAPIFixture sierraItemsAPIFixture;

	@Inject
	private RequestWorkflowContextHelper requestWorkflowContextHelper;

	@Inject
	private PlacePatronRequestAtBorrowingAgencyStateTransition placePatronRequestAtBorrowingAgencyStateTransition;

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

		agencyFixture.saveAgency(DataAgency.builder()
			.id(UUID.randomUUID())
			.code("ab6")
			.name("Test AB6")
			.hostLms(sierraHostLms)
			.build());

		this.sierraPatronsAPIFixture = sierraApiFixtureProvider.patronsApiFor(mockServerClient);
		this.sierraItemsAPIFixture = sierraApiFixtureProvider.itemsApiFor(mockServerClient);

		final var sierraItemsAPIFixture = sierraApiFixtureProvider.itemsApiFor(mockServerClient);
		final var sierraBibsAPIFixture = sierraApiFixtureProvider.bibsApiFor(mockServerClient);

		final var bibPatch = BibPatch.builder()
			.authors(List.of("Stafford Beer"))
			.titles(List.of("Brain of the Firm"))
			.build();

		sierraBibsAPIFixture.createPostBibsMock(bibPatch, 7916921);
		sierraItemsAPIFixture.successResponseForCreateItem(7916921, "ab6", "9849123490");

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

		referenceValueMappingFixture.defineLocationToAgencyMapping(HOST_LMS_CODE, "ab6", "ab6");
		referenceValueMappingFixture.defineLocationToAgencyMapping(
			INVALID_HOLD_POLICY_HOST_LMS_CODE, "ab6", "ab6");
		referenceValueMappingFixture.defineLocationToAgencyMapping(HOST_LMS_CODE,"ABC123","ab6");

	}

	@Test
	void placeRequestAtBorrowingAgencySucceeds() {
		// Arrange
		final var clusterRecordId = randomUUID();
		final var bibRecordId = randomUUID();

		final var clusterRecord = clusterRecordFixture.createClusterRecord(
			clusterRecordId, bibRecordId);

		final var hostLms = hostLmsFixture.findByCode(HOST_LMS_CODE);
		final var sourceSystemId = hostLms.getId();

		bibRecordFixture.createBibRecord(bibRecordId, sourceSystemId, "798472", clusterRecord);

		final var patron = patronFixture.savePatron("872321");

		patronFixture.saveIdentity(patron, hostLms, "872321", true, "-", "872321", null);

		final var patronRequestId = randomUUID();
		var patronRequest = PatronRequest.builder()
			.id(patronRequestId)
			.patron(patron)
			.bibClusterId(clusterRecordId)
			.status(REQUEST_PLACED_AT_SUPPLYING_AGENCY)
			.pickupLocationCodeContext(HOST_LMS_CODE)
			.pickupLocationCode("ABC123")
			.build();

		patronRequestsFixture.savePatronRequest(patronRequest);

		supplierRequestsFixture.saveSupplierRequest(randomUUID(), patronRequest, "76832", "localItemId",
			"ab6", "9849123490", hostLms.code, "ab6");

		sierraPatronsAPIFixture.mockPlacePatronHoldRequest("872321", "b", 7916921);

		// This one is for the borrower side hold - we now match a hold using the note instead of the itemid - so we have to fix up a hold with the
		// correct note containing the patronRequestId
		sierraPatronsAPIFixture.mockGetHoldsForPatronReturningSingleItemHold("872321",
			"https://sandbox.iii.com/iii/sierra-api/v6/patrons/holds/864902",
			"Consortial Hold. tno="+patronRequest.getId(), "872321");

		sierraItemsAPIFixture.mockGetItemById("872321",
			SierraItem.builder()
				.id("872321")
				.barcode("6736255")
				.statusCode("-")
				.build());

		// Act
		final var pr = placeRequestAtBorrowingAgency(patronRequest);

		// Assert
		assertThat("Patron request should not be null", pr, is(notNullValue()));
		assertThat("Status code wasn't expected.", pr.getStatus(), is(Status.REQUEST_PLACED_AT_BORROWING_AGENCY));
		assertThat("Local request id wasn't expected.", pr.getLocalRequestId(), is("864902"));
		assertThat("Local request status wasn't expected.", pr.getLocalRequestStatus(), is("CONFIRMED"));

		assertSuccessfulTransitionAudit(pr);

		sierraPatronsAPIFixture.verifyPlaceHoldRequestMade("872321", "b",
			7916921, "ABC123", "Consortial Hold. tno=" + pr.getId());
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

		final var patron = patronFixture.savePatron("972321");

		patronFixture.saveIdentity(patron, hostLms, "972321", true, "-", "972321", null);

		final var patronRequestId = randomUUID();
		var patronRequest = PatronRequest.builder()
			.id(patronRequestId)
			.patron(patron)
			.bibClusterId(clusterRecordId)
			.pickupLocationCode("ABC123")
			.pickupLocationCodeContext(HOST_LMS_CODE)
			.status(REQUEST_PLACED_AT_SUPPLYING_AGENCY)
			.build();

		patronRequestsFixture.savePatronRequest(patronRequest);

		supplierRequestsFixture.saveSupplierRequest(randomUUID(), patronRequest, "647245", "localItemId",
			"ab6", "9849123490", hostLms.code, "ab6");

		sierraPatronsAPIFixture.patronHoldRequestErrorResponse("972321", "b");

		// Act
		final var problem = assertThrows(ThrowableProblem.class,
			() -> placeRequestAtBorrowingAgency(patronRequest));

		// Assert
		final var expectedMessage = "Unexpected response from: POST /iii/sierra-api/v6/patrons/972321/holds/requests";

		assertThat(problem, allOf(
			hasMessage(expectedMessage),
			hasResponseStatusCodeParameter(500),
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

		final var patron = patronFixture.savePatron("972321");

		patronFixture.saveIdentity(patron, hostLms, "785843", true, "-", "972321", null);

		final var patronRequestId = randomUUID();
		var patronRequest = PatronRequest.builder()
			.id(patronRequestId)
			.patron(patron)
			.bibClusterId(clusterRecordId)
			.pickupLocationCode("ABC123")
			.pickupLocationCodeContext(HOST_LMS_CODE)
			.status(REQUEST_PLACED_AT_SUPPLYING_AGENCY)
			.build();

		patronRequestsFixture.savePatronRequest(patronRequest);

		supplierRequestsFixture.saveSupplierRequest(randomUUID(), patronRequest, "35365", "localItemId",
			"ab6", "9849123490", hostLms.code, "ab6");

		sierraPatronsAPIFixture.mockPlacePatronHoldRequest("785843", "b", 7916921);

		sierraPatronsAPIFixture.notFoundWhenGettingPatronRequests("785843");

		// Act
		final var exception = assertThrows(RuntimeException.class,
			() -> placeRequestAtBorrowingAgency(patronRequest));

		// Assert
		assertThat(exception.getMessage(),
			is("No hold request found for the given note: Consortial Hold. tno=" + patronRequestId));

		final var fetchedPatronRequest = patronRequestsFixture.findById(patronRequest.getId());

		assertThat("Request should have error status afterwards",
			fetchedPatronRequest.getStatus(), is(ERROR));

		assertThat("Request should have error message afterwards",
			fetchedPatronRequest.getErrorMessage(),
			is("No hold request found for the given note: Consortial Hold. tno=" + patronRequestId));

		assertUnsuccessfulTransitionAudit(fetchedPatronRequest,
			"No hold request found for the given note: Consortial Hold. tno=" + patronRequestId);
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

		final var patron = patronFixture.savePatron("872321");

		patronFixture.saveIdentity(patron, invalidHoldPolicyHostLms, "872321", true, "-", "872321", null);

		final var patronRequestId = randomUUID();
		var patronRequest = PatronRequest.builder()
			.id(patronRequestId)
			.patron(patron)
			.bibClusterId(clusterRecordId)
			.status(REQUEST_PLACED_AT_SUPPLYING_AGENCY)
			.pickupLocationCode("ABC123")
			.pickupLocationCodeContext(HOST_LMS_CODE)
			.build();

		patronRequestsFixture.savePatronRequest(patronRequest);

		supplierRequestsFixture.saveSupplierRequest(randomUUID(), patronRequest, "76832", "localItemId",
			"ab6", "9849123490", invalidHoldPolicyHostLms.code, "ab6");

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

		final var hostLms = hostLmsFixture.findByCode(HOST_LMS_CODE);

		final var patron = patronFixture.savePatron("872321");

		patronFixture.saveIdentity(patron, hostLms, "872321", true, "-", "872321", null);

		final var patronRequestId = randomUUID();
		var patronRequest = PatronRequest.builder()
			.id(patronRequestId)
			.patron(patron)
			.bibClusterId(clusterRecordId)
			.status(REQUEST_PLACED_AT_SUPPLYING_AGENCY)
			.pickupLocationCodeContext(HOST_LMS_CODE)
			.pickupLocationCode("ABC123")
			.build();

		patronRequestsFixture.savePatronRequest(patronRequest);

		supplierRequestsFixture.saveSupplierRequest(randomUUID(), patronRequest, "76832", "localItemId",
			"ab6", "9849123490", hostLms.code, "ab6");

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
	void shouldFailWhenClusterRecordCannotBeFound() {
		// Arrange
		final var clusterRecordId = randomUUID();

		final var hostLms = hostLmsFixture.findByCode(HOST_LMS_CODE);

		final var patron = patronFixture.savePatron("872321");

		patronFixture.saveIdentity(patron, hostLms, "872321", true, "-", "872321", null);

		final var patronRequestId = randomUUID();
		var patronRequest = PatronRequest.builder()
			.id(patronRequestId)
			.patron(patron)
			.bibClusterId(clusterRecordId)
			.status(REQUEST_PLACED_AT_SUPPLYING_AGENCY)
			.pickupLocationCode("ABC123")
			.pickupLocationCodeContext(HOST_LMS_CODE)
			.build();

		patronRequestsFixture.savePatronRequest(patronRequest);

		supplierRequestsFixture.saveSupplierRequest(randomUUID(), patronRequest, "76832", "localItemId",
			"ab6", "9849123490", hostLms.code, "ab6");

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

	private void assertSuccessfulTransitionAudit(PatronRequest patronRequest) {
		final var fetchedAudit = patronRequestsFixture.findOnlyAuditEntry(patronRequest);

		assertThat("Patron Request audit should NOT have brief description",
			fetchedAudit.getBriefDescription(), is(nullValue()));

		assertThat("Patron Request audit should have from state",
			fetchedAudit.getFromStatus(), is(REQUEST_PLACED_AT_SUPPLYING_AGENCY));

		assertThat("Patron Request audit should have to state",
			fetchedAudit.getToStatus(), is(Status.REQUEST_PLACED_AT_BORROWING_AGENCY));
	}

	private void assertUnsuccessfulTransitionAudit(PatronRequest patronRequest, String description) {
		final var fetchedAudit = patronRequestsFixture.findOnlyAuditEntry(patronRequest);

		assertThat("Patron Request audit should have brief description",
			fetchedAudit.getBriefDescription(), is(description));

		assertThat("Patron Request audit should have from state",
			fetchedAudit.getFromStatus(), is(REQUEST_PLACED_AT_SUPPLYING_AGENCY));

		assertThat("Patron Request audit should have to state",
			fetchedAudit.getToStatus(), is(ERROR));
	}

	private PatronRequest placeRequestAtBorrowingAgency(PatronRequest patronRequest) {

		return requestWorkflowContextHelper.fromPatronRequest(patronRequest)
				.flatMap(ctx -> placePatronRequestAtBorrowingAgencyStateTransition.attempt(ctx) )
				.thenReturn(patronRequest)
				.block();
	}
}
