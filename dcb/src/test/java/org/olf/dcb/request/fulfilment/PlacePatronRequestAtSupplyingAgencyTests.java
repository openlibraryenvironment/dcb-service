package org.olf.dcb.request.fulfilment;

import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.olf.dcb.core.model.PatronRequest.Status.ERROR;
import static org.olf.dcb.core.model.PatronRequest.Status.REQUEST_PLACED_AT_SUPPLYING_AGENCY;
import static org.olf.dcb.core.model.PatronRequest.Status.RESOLVED;
import static org.olf.dcb.test.matchers.PatronRequestAuditMatchers.briefDescriptionContains;
import static org.olf.dcb.test.matchers.PatronRequestAuditMatchers.hasFromStatus;
import static org.olf.dcb.test.matchers.PatronRequestAuditMatchers.hasNoBriefDescription;
import static org.olf.dcb.test.matchers.PatronRequestAuditMatchers.hasToStatus;

import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockserver.client.MockServerClient;
import org.olf.dcb.core.interaction.sierra.SierraApiFixtureProvider;
import org.olf.dcb.core.interaction.sierra.SierraPatronsAPIFixture;
import org.olf.dcb.core.model.DataAgency;
import org.olf.dcb.core.model.DataHostLms;
import org.olf.dcb.core.model.Patron;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.request.workflow.PlacePatronRequestAtSupplyingAgencyStateTransition;
import org.olf.dcb.test.AgencyFixture;
import org.olf.dcb.test.ClusterRecordFixture;
import org.olf.dcb.test.HostLmsFixture;
import org.olf.dcb.test.PatronFixture;
import org.olf.dcb.test.PatronRequestsFixture;
import org.olf.dcb.test.ReferenceValueMappingFixture;
import org.olf.dcb.test.SupplierRequestsFixture;
import org.zalando.problem.DefaultProblem;

import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import services.k_int.interaction.sierra.SierraTestUtils;
import services.k_int.test.mockserver.MockServerMicronautTest;

@Slf4j
@MockServerMicronautTest
@TestInstance(PER_CLASS)
class PlacePatronRequestAtSupplyingAgencyTests {
	private static final String HOST_LMS_CODE = "supplying-agency-service-tests";
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
	private SupplierRequestsFixture supplierRequestsFixture;
	@Inject
	private ReferenceValueMappingFixture referenceValueMappingFixture;
	@Inject
	private PlacePatronRequestAtSupplyingAgencyStateTransition placePatronRequestAtSupplyingAgencyStateTransition;
	@Inject
	private AgencyFixture agencyFixture;
	@Inject
	private PatronService patronService;

	private SierraPatronsAPIFixture sierraPatronsAPIFixture;

	private DataAgency agency_ab6 = null;

	@BeforeAll
	public void beforeAll(MockServerClient mockServerClient) {
		final String TOKEN = "test-token";
		final String BASE_URL = "https://supplying-agency-service-tests.com";
		final String KEY = "supplying-agency-service-key";
		final String SECRET = "supplying-agency-service-secret";

		SierraTestUtils.mockFor(mockServerClient, BASE_URL)
			.setValidCredentials(KEY, SECRET, TOKEN, 60);

		hostLmsFixture.deleteAll();

		DataHostLms sierraHostLms = hostLmsFixture.createSierraHostLms(HOST_LMS_CODE,
			KEY, SECRET, BASE_URL, "title");

		hostLmsFixture.createSierraHostLms(INVALID_HOLD_POLICY_HOST_LMS_CODE,
			KEY, SECRET, BASE_URL, "invalid");

		this.agency_ab6 = agencyFixture.saveAgency(
			DataAgency.builder().id(randomUUID()).code("ab6").name("name").hostLms(sierraHostLms).build());
		referenceValueMappingFixture.deleteAll();

		this.sierraPatronsAPIFixture = sierraApiFixtureProvider.patronsApiFor(mockServerClient);

		// patron hold requests success
		sierraPatronsAPIFixture.patronHoldRequestResponse("1000002", "b", 563653);
		sierraPatronsAPIFixture.patronHoldRequestResponse("1000003", "b", 563653);

		// add patron type mappings
		savePatronTypeMappings();
		saveHomeLibraryMappings();
	}

	@DisplayName("patron is known to supplier and places patron request with the unexpected patron type")
	@Test
	void shouldReturnPlacedAtSupplyingAgencyWhenPatronIsKnownToSupplierWithAnUnexpectedPtype() {
		// Arrange
		final var localId = "872321";
		final var patronRequestId = randomUUID();
		final var clusterRecordId = createClusterRecord();
		final var hostLms = hostLmsFixture.findByCode(HOST_LMS_CODE);
		final var patron = createPatron(localId, hostLms);
		final var patronRequest = savePatronRequest(patronRequestId, patron, clusterRecordId);
		saveSupplierRequest(patronRequest, hostLms.getCode());

		sierraPatronsAPIFixture.patronResponseForUniqueId("u", "872321@ab6");

		// The unexpected patron type will trigger a request to update the virtual patron
		sierraPatronsAPIFixture.updatePatron("1000002");

		sierraPatronsAPIFixture.patronHoldResponse("1000002",
			"https://sandbox.iii.com/iii/sierra-api/v6/patrons/holds/864904",
			"Consortial Hold. tno="+patronRequest.getId());

		// Act
		final var pr = placePatronRequestAtSupplyingAgencyStateTransition
			.attempt(patronRequest)
			.block();

		// Assert
		assertThat("Patron request id wasn't expected.", pr.getId(), is(patronRequestId));
		assertThat("Status wasn't expected.", pr.getStatus(), is(REQUEST_PLACED_AT_SUPPLYING_AGENCY));

		assertSuccessfulTransitionAudit(pr);

		sierraPatronsAPIFixture.verifyFindPatronRequestMade("872321@ab6");
		sierraPatronsAPIFixture.verifyCreatePatronRequestNotMade("872321@ab6");
		sierraPatronsAPIFixture.verifyUpdatePatronRequestMade("1000002");
	}

	@DisplayName("patron is known to supplier and places patron request with the expected patron type")
	@Test
	void shouldReturnPlacedAtSupplyingAgencyWhenPatronIsKnownToSupplierWithTheExpectedPtype() {
		// Arrange
		final var localId = "32453";
		final var patronRequestId = randomUUID();
		final var clusterRecordId = createClusterRecord();
		final var hostLms = hostLmsFixture.findByCode(HOST_LMS_CODE);
		final var patron = createPatron(localId, hostLms);
		final var patronRequest = savePatronRequest(patronRequestId, patron, clusterRecordId);
		saveSupplierRequest(patronRequest, hostLms.getCode());

		sierraPatronsAPIFixture.patronResponseForUniqueIdExpectedPtype("u", "32453@ab6");

		sierraPatronsAPIFixture.patronHoldResponse("1000002",
			"https://sandbox.iii.com/iii/sierra-api/v6/patrons/holds/864904",
			"Consortial Hold. tno="+patronRequest.getId());

		// Act
		final var pr = placePatronRequestAtSupplyingAgencyStateTransition
			.attempt(patronRequest)
			.block();

		// Assert
		assertThat("Patron request id wasn't expected.", pr.getId(), is(patronRequestId));
		assertThat("Status wasn't expected.", pr.getStatus(), is(REQUEST_PLACED_AT_SUPPLYING_AGENCY));

		assertSuccessfulTransitionAudit(pr);

		sierraPatronsAPIFixture.verifyFindPatronRequestMade("32453@ab6");
		sierraPatronsAPIFixture.verifyCreatePatronRequestNotMade("32453@ab6");
		sierraPatronsAPIFixture.verifyUpdatePatronRequestNotMade("1000002");
	}

	@DisplayName("patron is not known to supplier and places patron request")
	@Test
	void shouldReturnPlacedAtSupplyingAgencyWhenPatronIsNotKnownToSupplier() {
		// Arrange
		final var localId = "546730";
		final var patronRequestId = randomUUID();
		final var clusterRecordId = createClusterRecord();
		final var hostLms = hostLmsFixture.findByCode(HOST_LMS_CODE);
		final var patron = createPatron(localId, hostLms);
		final var patronRequest = savePatronRequest(patronRequestId, patron, clusterRecordId);
		saveSupplierRequest(patronRequest, hostLms.getCode());

		sierraPatronsAPIFixture.patronNotFoundResponseForUniqueId("u", "546730@ab6");
		sierraPatronsAPIFixture.postPatronResponse("546730@ab6", 1000003);
		sierraPatronsAPIFixture.patronHoldResponse("1000003",
			"https://sandbox.iii.com/iii/sierra-api/v6/patrons/holds/864905",
			"Consortial Hold. tno="+patronRequest.getId());

		// Act
		final var pr = placePatronRequestAtSupplyingAgencyStateTransition
			.attempt(patronRequest)
			.block();

		// Assert
		assertThat("Patron request id wasn't expected.", pr.getId(), is(patronRequestId));
		assertThat("Status wasn't expected.", pr.getStatus(), is(REQUEST_PLACED_AT_SUPPLYING_AGENCY));

		assertSuccessfulTransitionAudit(pr);

		sierraPatronsAPIFixture.verifyFindPatronRequestMade("546730@ab6");
		sierraPatronsAPIFixture.verifyCreatePatronRequestMade("546730@ab6");
	}

	@DisplayName("request cannot be placed in supplying agency’s local system")
	@Test
	void placePatronRequestAtSupplyingAgencyReturns500response() {
		// Arrange
		final var localId = "931824";
		final var patronRequestId = randomUUID();
		final var clusterRecordId = createClusterRecord();
		final var hostLms = hostLmsFixture.findByCode(HOST_LMS_CODE);
		final var patron = createPatron(localId, hostLms);
		final var patronRequest = savePatronRequest(patronRequestId, patron, clusterRecordId);
		saveSupplierRequest(patronRequest, hostLms.getCode());

		sierraPatronsAPIFixture.patronNotFoundResponseForUniqueId("u", "931824@ab6");
		sierraPatronsAPIFixture.postPatronResponse("931824@ab6", 1000001);
		sierraPatronsAPIFixture.patronHoldRequestErrorResponse("1000001", "b");

		// Act
		final var exception = assertThrows(DefaultProblem.class,
			() -> placePatronRequestAtSupplyingAgencyStateTransition.attempt(patronRequest).block());

		// Assert
		final var expectedMessage = "Internal server error: Invalid configuration - [109 / 0]";

		assertThat("Should report exception message", exception.getMessage(), containsString(expectedMessage));

		patronRequestHasError(patronRequest, expectedMessage);
	}

	@Test
	void shouldFailWhenSierraHostLmsHasInvalidHoldPolicyConfiguration() {
		// Arrange
		final var localId = "872321";
		final var patronRequestId = randomUUID();
		final var clusterRecordId = createClusterRecord();
		final var hostLms = hostLmsFixture.findByCode(INVALID_HOLD_POLICY_HOST_LMS_CODE);
		final var patron = createPatron(localId, hostLms);
		final var patronRequest = savePatronRequest(patronRequestId, patron, clusterRecordId);
		saveSupplierRequest(patronRequest, INVALID_HOLD_POLICY_HOST_LMS_CODE);

		supplierRequestsFixture.saveSupplierRequest(randomUUID(), patronRequest, "76832", "localItemId",
			"ab6", "9849123490", INVALID_HOLD_POLICY_HOST_LMS_CODE);

		// Act
		final var exception = assertThrows(RuntimeException.class,
			() -> placePatronRequestAtSupplyingAgencyStateTransition.attempt(patronRequest).block());

		// Assert
		final var expectedErrorMessage = "Invalid hold policy for Host LMS";

		assertThat("Should have invalid hold policy message",
			exception.getMessage(), containsString(expectedErrorMessage));

		patronRequestHasError(patronRequest, expectedErrorMessage);
	}

	public void assertSuccessfulTransitionAudit(PatronRequest patronRequest) {
		final var onlyAuditEntry = patronRequestsFixture.findOnlyAuditEntry(patronRequest);

		assertThat(onlyAuditEntry, allOf(
			hasNoBriefDescription(),
			hasFromStatus(RESOLVED),
			hasToStatus(REQUEST_PLACED_AT_SUPPLYING_AGENCY)
		));
	}

	private void patronRequestHasError(PatronRequest patronRequest, String expectedErrorMessage) {
		final var fetchedPatronRequest = patronRequestsFixture.findById(patronRequest.getId());

		assertThat("Request should have error status afterwards",
			fetchedPatronRequest.getStatus(), is(ERROR));

		assertThat("Request should have error message afterwards",
			fetchedPatronRequest.getErrorMessage(), containsString(expectedErrorMessage));

		assertUnsuccessfulTransitionAudit(fetchedPatronRequest, expectedErrorMessage);
	}

	private void assertUnsuccessfulTransitionAudit(PatronRequest patronRequest, String description) {
		final var onlyAuditEntry = patronRequestsFixture.findOnlyAuditEntry(patronRequest);

		assertThat(onlyAuditEntry, allOf(
			briefDescriptionContains(description),
			hasFromStatus(RESOLVED),
			hasToStatus(ERROR)
		));
	}

	private UUID createClusterRecord() {
		return clusterRecordFixture.createClusterRecord(randomUUID(), null).getId();
	}

	private Patron createPatron(String localId, DataHostLms hostLms) {
		if (agency_ab6 == null) {
			throw new RuntimeException("Fixtures have not properly initialised data agency ab6");
		}

		final Patron patron = patronFixture.savePatron("123456");
		patronFixture.saveIdentity(patron, hostLms, localId, true, "1", "123456", agency_ab6);
		patronFixture.saveIdentity(patron, hostLms, localId, false, "1", null, null);
		patron.setPatronIdentities(patronService.findAllPatronIdentitiesByPatron(patron).collectList().block());
		return patron;
	}

	private PatronRequest savePatronRequest(UUID patronRequestId, Patron patron,
		UUID clusterRecordId) {

		final var requestingIdentity = patron.getPatronIdentities().get(1);

		var patronRequest = PatronRequest.builder()
			.id(patronRequestId)
			.patron(patron)
			.requestingIdentity(requestingIdentity)
			.bibClusterId(clusterRecordId)
			.pickupLocationCode("ABC123")
			.status(RESOLVED)
			.build();

		return patronRequestsFixture.savePatronRequest(patronRequest);
	}

	private void saveSupplierRequest(PatronRequest patronRequest, String hostLmsCode) {
		supplierRequestsFixture.saveSupplierRequest(
			randomUUID(), patronRequest, "563653", "7916922",
			"ab6", "9849123490", hostLmsCode);
	}

	private void savePatronTypeMappings() {
		referenceValueMappingFixture.defineNumericPatronTypeRangeMapping(HOST_LMS_CODE, 1, 1, "DCB", "SQUIGGLE");
		referenceValueMappingFixture.defineNumericPatronTypeRangeMapping(HOST_LMS_CODE,10,15, "DCB", "SQUIGGLE");
		referenceValueMappingFixture.defineNumericPatronTypeRangeMapping(HOST_LMS_CODE,20,25, "DCB", "SQUIGGLE");

		referenceValueMappingFixture.defineNumericPatronTypeRangeMapping(
			INVALID_HOLD_POLICY_HOST_LMS_CODE, 1, 1, "DCB", "SQUIGGLE");

		referenceValueMappingFixture.defineNumericPatronTypeRangeMapping(
			INVALID_HOLD_POLICY_HOST_LMS_CODE, 10,15, "DCB", "SQUIGGLE");

		referenceValueMappingFixture.defineNumericPatronTypeRangeMapping(
			INVALID_HOLD_POLICY_HOST_LMS_CODE, 20,25, "DCB", "SQUIGGLE");

		referenceValueMappingFixture.definePatronTypeMapping("DCB", "SQUIGGLE", HOST_LMS_CODE, "15");
		referenceValueMappingFixture.definePatronTypeMapping(
			"DCB", "SQUIGGLE", INVALID_HOLD_POLICY_HOST_LMS_CODE, "15");

		referenceValueMappingFixture.defineLocationToAgencyMapping("ABC123", "ab6");
	}

	private void saveHomeLibraryMappings() {
		// Tell systems how to convert supplying-agency-service-tests:123456 to ab6
		referenceValueMappingFixture.defineLocationToAgencyMapping(HOST_LMS_CODE, "123456", "ab6");
	}
}
