package org.olf.dcb.request.fulfilment;
import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.olf.dcb.core.model.PatronRequest.Status.ERROR;

import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockserver.client.MockServerClient;
import org.olf.dcb.core.interaction.sierra.SierraPatronsAPIFixture;
import org.olf.dcb.core.model.DataAgency;
import org.olf.dcb.core.model.DataHostLms;
import org.olf.dcb.core.model.Patron;
import org.olf.dcb.core.model.PatronIdentity;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.PatronRequest.Status;
import org.olf.dcb.core.model.ReferenceValueMapping;
import org.olf.dcb.request.workflow.PlacePatronRequestAtSupplyingAgencyStateTransition;
import org.olf.dcb.test.AgencyFixture;
import org.olf.dcb.test.ClusterRecordFixture;
import org.olf.dcb.test.HostLmsFixture;
import org.olf.dcb.test.PatronFixture;
import org.olf.dcb.test.PatronRequestsFixture;
import org.olf.dcb.test.ReferenceValueMappingFixture;
import org.olf.dcb.test.SupplierRequestsFixture;

import org.olf.dcb.request.fulfilment.PatronService;

import reactor.core.publisher.Mono;

import io.micronaut.core.io.ResourceLoader;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import jakarta.inject.Inject;
import services.k_int.interaction.sierra.SierraTestUtils;
import services.k_int.test.mockserver.MockServerMicronautTest;
@MockServerMicronautTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PlacePatronRequestAtSupplyingAgencyTests {
	private static final String HOST_LMS_CODE = "supplying-agency-service-tests";
	@Inject
	ResourceLoader loader;
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


        private DataAgency agency_ab6 = null;

	private SierraPatronsAPIFixture sierraPatronsAPIFixture;


	@BeforeAll
	public void beforeAll(MockServerClient mock) {
		final String TOKEN = "test-token";
		final String BASE_URL = "https://supplying-agency-service-tests.com";
		final String KEY = "supplying-agency-service-key";
		final String SECRET = "supplying-agency-service-secret";
		SierraTestUtils.mockFor(mock, BASE_URL) .setValidCredentials(KEY, SECRET, TOKEN, 60);
		hostLmsFixture.deleteAllHostLMS();
		DataHostLms d1 = hostLmsFixture.createSierraHostLms(KEY, SECRET, BASE_URL, HOST_LMS_CODE);
                this.agency_ab6 = agencyFixture.saveAgency(new DataAgency(randomUUID(), "ab6", "name", d1));

		referenceValueMappingFixture.deleteAllReferenceValueMappings();
		this.sierraPatronsAPIFixture = new SierraPatronsAPIFixture(mock, loader);
		// patron hold requests success
		sierraPatronsAPIFixture.patronHoldRequestResponse("1000002");
		sierraPatronsAPIFixture.patronHoldRequestResponse("1000003");
		// add patron type mappings
		savePatronTypeMappings();
                saveHomeLibraryMappings(d1,agency_ab6);
	}

	@DisplayName("patron is known to supplier and places patron request with the unexpected patron type")
	@Test
	void shouldReturnPlacedAtSupplyingAgencyWhenPatronIsKnownToSupplierWithAnUnexpectedPtype() {
		// Arrange
		final var localId = "872321";
		final var homeLibraryCode = "123456";
		final var patronRequestId = randomUUID();
		final var clusterRecordId = createClusterRecord();
		final var hostLms = hostLmsFixture.findByCode(HOST_LMS_CODE);
		final var patron = createPatron(localId, hostLms);
		var patronRequest = savePatronRequest(patronRequestId, patron, clusterRecordId);
		saveSupplierRequest(patronRequest, hostLms.code);
		// sierraPatronsAPIFixture.patronResponseForUniqueId("872321@123456");
		sierraPatronsAPIFixture.patronResponseForUniqueId("872321@ab6");

		// The unexpected Ptype will use this mock to update the virtual patron
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
		assertThat("Status wasn't expected.", pr.getStatus(), is(Status.REQUEST_PLACED_AT_SUPPLYING_AGENCY));
		assertSuccessfulTransitionAudit(pr);
	}
	@DisplayName("patron is known to supplier and places patron request with the expected patron type")
	@Test
	void shouldReturnPlacedAtSupplyingAgencyWhenPatronIsKnownToSupplierWithTheExpectedPtype() {
		// Arrange
		final var localId = "32453";
		final var homeLibraryCode = "123456";
		final var patronRequestId = randomUUID();
		final var clusterRecordId = createClusterRecord();
		final var hostLms = hostLmsFixture.findByCode(HOST_LMS_CODE);
		final var patron = createPatron(localId, hostLms);
		var patronRequest = savePatronRequest(patronRequestId, patron, clusterRecordId);
		saveSupplierRequest(patronRequest, hostLms.code);
		// sierraPatronsAPIFixture.patronResponseForUniqueIdExpectedPtype("32453@123456");
		sierraPatronsAPIFixture.patronResponseForUniqueIdExpectedPtype("32453@ab6");
		sierraPatronsAPIFixture.patronHoldResponse("1000002",
			"https://sandbox.iii.com/iii/sierra-api/v6/patrons/holds/864904",
			"Consortial Hold. tno="+patronRequest.getId());
		// Act
		final var pr = placePatronRequestAtSupplyingAgencyStateTransition
			.attempt(patronRequest)
			.block();
		// Assert
		assertThat("Patron request id wasn't expected.", pr.getId(), is(patronRequestId));
		assertThat("Status wasn't expected.", pr.getStatus(), is(Status.REQUEST_PLACED_AT_SUPPLYING_AGENCY));
		assertSuccessfulTransitionAudit(pr);
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
		var patronRequest = savePatronRequest(patronRequestId, patron, clusterRecordId);
		saveSupplierRequest(patronRequest, hostLms.code);
		// sierraPatronsAPIFixture.patronNotFoundResponseForUniqueId("546730@123456");
		sierraPatronsAPIFixture.patronNotFoundResponseForUniqueId("546730@ab6");
		// sierraPatronsAPIFixture.postPatronResponse("546730@123456", 1000003);
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
		assertThat("Status wasn't expected.", pr.getStatus(), is(Status.REQUEST_PLACED_AT_SUPPLYING_AGENCY));
		assertSuccessfulTransitionAudit(pr);
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

		var patronRequest = savePatronRequest(patronRequestId, patron, clusterRecordId);
		saveSupplierRequest(patronRequest, hostLms.code);

		// sierraPatronsAPIFixture.patronNotFoundResponseForUniqueId("931824@123456");
		sierraPatronsAPIFixture.patronNotFoundResponseForUniqueId("931824@ab6");
		// sierraPatronsAPIFixture.postPatronResponse("931824@123456", 1000001);
		sierraPatronsAPIFixture.postPatronResponse("931824@ab6", 1000001);
		sierraPatronsAPIFixture.patronHoldRequestErrorResponse("1000001");

		// Act
		final var exception = assertThrows(HttpClientResponseException.class,
			() -> placePatronRequestAtSupplyingAgencyStateTransition.attempt(patronRequest).block());

		// Assert
		final var expectedMessage = "Internal server error: Invalid configuration - [109 / 0]";

		assertThat("Should report exception message", exception.getMessage(), is(expectedMessage));

		final var fetchedPatronRequest = patronRequestsFixture.findById(patronRequest.getId());

		assertThat("Request should have error status afterwards",
			fetchedPatronRequest.getStatus(), is(ERROR));

		assertThat("Request should have error message afterwards",
			fetchedPatronRequest.getErrorMessage(), is(expectedMessage));

		assertUnsuccessfulTransitionAudit(fetchedPatronRequest, expectedMessage);
	}

	public void assertSuccessfulTransitionAudit(PatronRequest patronRequest) {
		final var fetchedAudit = patronRequestsFixture.findAuditByPatronRequest(patronRequest).blockFirst();
		assertThat("Patron Request audit should NOT have brief description",
			fetchedAudit.getBriefDescription(),
			is(nullValue()));
		assertThat("Patron Request audit should have from state",
			fetchedAudit.getFromStatus(), is(Status.RESOLVED));
		assertThat("Patron Request audit should have to state",
			fetchedAudit.getToStatus(), is(Status.REQUEST_PLACED_AT_SUPPLYING_AGENCY));
	}
	public void assertUnsuccessfulTransitionAudit(PatronRequest patronRequest, String description) {
		final var fetchedAudit = patronRequestsFixture.findAuditByPatronRequest(patronRequest).blockFirst();
		assertThat("Patron Request audit should have brief description",
			fetchedAudit.getBriefDescription(),
			is(description));
		assertThat("Patron Request audit should have from state",
			fetchedAudit.getFromStatus(), is(Status.RESOLVED));
		assertThat("Patron Request audit should have to state",
			fetchedAudit.getToStatus(), is(ERROR));
	}
	private UUID createClusterRecord() {
		final UUID clusterRecordId = randomUUID();
		clusterRecordFixture.createClusterRecord(clusterRecordId);
		return clusterRecordId;
	}
	private Patron createPatron(String localId, DataHostLms hostLms) {

                if ( agency_ab6 == null )
                        throw new RuntimeException("Fixtures have not properly initialised data agency ab6");

		final Patron patron = patronFixture.savePatron("123456");
		patronFixture.saveIdentity(patron, hostLms, localId, true, "-", "123456", agency_ab6);
		patronFixture.saveIdentity(patron, hostLms, localId, false, "-", null, null);
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
			// .status(Status.REQUEST_PLACED_AT_BORROWING_AGENCY)
			.status(Status.RESOLVED)
			.build();
		patronRequestsFixture.savePatronRequest(patronRequest);
		return patronRequest;
	}
	private void saveSupplierRequest(PatronRequest patronRequest, String hostLmsCode) {
		supplierRequestsFixture.saveSupplierRequest(
			randomUUID(),
			patronRequest,
			"7916922",
			"ab6",
			"9849123490",
			hostLmsCode
		);
	}
	private void savePatronTypeMappings() {

		referenceValueMappingFixture.saveReferenceValueMapping(
			patronFixture.createPatronTypeMapping(
				"supplying-agency-service-tests", "-", "DCB", "-"));

		referenceValueMappingFixture.saveReferenceValueMapping(
			patronFixture.createPatronTypeMapping(
				"DCB", "-", "supplying-agency-service-tests", "15"));

                ReferenceValueMapping pul = ReferenceValueMapping.builder().id(randomUUID()).fromCategory("PickupLocation")
                                .fromContext("DCB").fromValue("ABC123").toCategory("AGENCY").toContext("DCB").toValue("ab6")
                                .build();
                referenceValueMappingFixture.saveReferenceValueMapping(pul);

	}
        private void saveHomeLibraryMappings(DataHostLms d1, DataAgency dataAgency) {

                // Tell systems how to convert supplying-agency-service-tests:123456 to ab6
                ReferenceValueMapping rvm = ReferenceValueMapping.builder().id(randomUUID()).fromCategory("location")
                                .fromContext("supplying-agency-service-tests").fromValue("123456").toCategory("agency").toContext("dcb").toValue("ab6")
                                .build();

                referenceValueMappingFixture.saveReferenceValueMapping(rvm);
        }
}
