package org.olf.dcb.request.fulfilment;

import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.containsString;
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
import static org.olf.dcb.test.matchers.PatronRequestMatchers.hasErrorMessage;
import static org.olf.dcb.test.matchers.PatronRequestMatchers.hasId;
import static org.olf.dcb.test.matchers.PatronRequestMatchers.hasStatus;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
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

	private DataAgency supplyingAgency = null;

	@BeforeEach
	public void beforeEach(MockServerClient mockServerClient) {
		final String TOKEN = "test-token";
		final String BASE_URL = "https://supplying-agency-service-tests.com";
		final String KEY = "supplying-agency-service-key";
		final String SECRET = "supplying-agency-service-secret";

		SierraTestUtils.mockFor(mockServerClient, BASE_URL)
			.setValidCredentials(KEY, SECRET, TOKEN, 60);

		hostLmsFixture.deleteAll();
		referenceValueMappingFixture.deleteAll();

		final var sierraHostLms = hostLmsFixture.createSierraHostLms(HOST_LMS_CODE,
			KEY, SECRET, BASE_URL, "title");

		this.supplyingAgency = agencyFixture.saveAgency(DataAgency.builder()
			.id(randomUUID())
			.code("supplying-agency")
			.name("Supplying Agency")
			.hostLms(sierraHostLms)
			.build());

		this.sierraPatronsAPIFixture = sierraApiFixtureProvider.patronsApiFor(mockServerClient);

		// add patron type mappings
		savePatronTypeMappings();
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

		sierraPatronsAPIFixture.patronFoundResponse("u", "872321@supplying-agency",
			SierraPatronsAPIFixture.Patron.builder()
				.id(1000002)
				.patronType(22)
				.names(List.of("Joe Bloggs"))
				.homeLibraryCode("testbbb")
				.build());

		// The unexpected patron type will trigger a request to update the virtual patron
		sierraPatronsAPIFixture.updatePatron("1000002");

		sierraPatronsAPIFixture.patronHoldResponse("1000002",
			"https://sandbox.iii.com/iii/sierra-api/v6/patrons/holds/864904",
			"Consortial Hold. tno="+patronRequest.getId());

		sierraPatronsAPIFixture.patronHoldRequestResponse("1000002", "b", 563653);

		// Act
		final var placedPatronRequest = placeAtSupplyingAgency(patronRequest);

		// Assert
		patronRequestWasPlaced(placedPatronRequest, patronRequestId);

		sierraPatronsAPIFixture.verifyFindPatronRequestMade("872321@supplying-agency");
		sierraPatronsAPIFixture.verifyCreatePatronRequestNotMade("872321@supplying-agency");
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

		sierraPatronsAPIFixture.patronFoundResponse("u", "32453@supplying-agency",
			SierraPatronsAPIFixture.Patron.builder()
				.id(1000002)
				.patronType(15)
				.homeLibraryCode("testccc")
				.barcodes(List.of("647647746"))
				.names(List.of("Bob"))
				.build());

		sierraPatronsAPIFixture.patronHoldResponse("1000002",
			"https://sandbox.iii.com/iii/sierra-api/v6/patrons/holds/864904",
			"Consortial Hold. tno="+patronRequest.getId());

		sierraPatronsAPIFixture.patronHoldRequestResponse("1000002", "b", 563653);

		// Act
		final var placedPatronRequest = placeAtSupplyingAgency(patronRequest);

		// Assert
		patronRequestWasPlaced(placedPatronRequest, patronRequestId);

		sierraPatronsAPIFixture.verifyFindPatronRequestMade("32453@supplying-agency");
		sierraPatronsAPIFixture.verifyCreatePatronRequestNotMade("32453@supplying-agency");
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

		sierraPatronsAPIFixture.patronNotFoundResponse("u", "546730@supplying-agency");
		sierraPatronsAPIFixture.postPatronResponse("546730@supplying-agency", 1000003);
		sierraPatronsAPIFixture.patronHoldResponse("1000003",
			"https://sandbox.iii.com/iii/sierra-api/v6/patrons/holds/864905",
			"Consortial Hold. tno="+patronRequest.getId());

		sierraPatronsAPIFixture.patronHoldRequestResponse("1000003", "b", 563653);

		// Act
		final var placedPatronRequest = placeAtSupplyingAgency(patronRequest);

		// Assert
		patronRequestWasPlaced(placedPatronRequest, patronRequestId);

		sierraPatronsAPIFixture.verifyFindPatronRequestMade("546730@supplying-agency");
		sierraPatronsAPIFixture.verifyCreatePatronRequestMade("546730@supplying-agency");
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

		sierraPatronsAPIFixture.patronNotFoundResponse("u", "931824@supplying-agency");
		sierraPatronsAPIFixture.postPatronResponse("931824@supplying-agency", 1000001);
		sierraPatronsAPIFixture.patronHoldRequestErrorResponse("1000001", "b");

		// Act
		final var exception = assertThrows(DefaultProblem.class,
			() -> placeAtSupplyingAgency(patronRequest));

		// Assert
		final var expectedMessage = "Internal server error: Invalid configuration - [109 / 0]";

		assertThat("Should report exception message", exception.getMessage(), containsString(expectedMessage));

		final var fetchedPatronRequest = patronRequestsFixture.findById(patronRequest.getId());

		assertThat(fetchedPatronRequest, allOf(
			hasStatus(ERROR),
			hasErrorMessage(expectedMessage)
		));

		final var onlyAuditEntry = patronRequestsFixture.findOnlyAuditEntry(fetchedPatronRequest);

		assertThat(onlyAuditEntry, allOf(
			briefDescriptionContains(expectedMessage),
			hasFromStatus(RESOLVED),
			hasToStatus(ERROR)
		));
	}

	private void patronRequestWasPlaced(PatronRequest patronRequest, UUID expectedId) {
		assertThat(patronRequest, allOf(
			hasId(expectedId),
			hasStatus(REQUEST_PLACED_AT_SUPPLYING_AGENCY)
		));

		final var onlyAuditEntry = patronRequestsFixture.findOnlyAuditEntry(patronRequest);

		assertThat(onlyAuditEntry, allOf(
			hasNoBriefDescription(),
			hasFromStatus(RESOLVED),
			hasToStatus(REQUEST_PLACED_AT_SUPPLYING_AGENCY)
		));
	}

	private UUID createClusterRecord() {
		return clusterRecordFixture.createClusterRecord(randomUUID(), null).getId();
	}

	private Patron createPatron(String localId, DataHostLms hostLms) {
		if (supplyingAgency == null) {
			throw new RuntimeException("Fixtures have not properly initialised data agency supplying-agency");
		}

		final Patron patron = patronFixture.savePatron("123456");
		patronFixture.saveIdentity(patron, hostLms, localId, true, "1", "123456",
			supplyingAgency);
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
			"supplying-agency", "9849123490", hostLmsCode);
	}

	private PatronRequest placeAtSupplyingAgency(PatronRequest patronRequest) {
		return placePatronRequestAtSupplyingAgencyStateTransition.attempt(patronRequest).block();
	}

	private void savePatronTypeMappings() {
		referenceValueMappingFixture.defineNumericPatronTypeRangeMapping(HOST_LMS_CODE, 1, 1, "DCB", "SQUIGGLE");
		referenceValueMappingFixture.defineNumericPatronTypeRangeMapping(HOST_LMS_CODE,10,15, "DCB", "SQUIGGLE");
		referenceValueMappingFixture.defineNumericPatronTypeRangeMapping(HOST_LMS_CODE,20,25, "DCB", "SQUIGGLE");

		referenceValueMappingFixture.definePatronTypeMapping("DCB", "SQUIGGLE", HOST_LMS_CODE, "15");

		referenceValueMappingFixture.defineLocationToAgencyMapping("ABC123", "supplying-agency");
	}
}
