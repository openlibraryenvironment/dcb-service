package org.olf.dcb.request.fulfilment;

import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.olf.dcb.core.model.PatronRequest.Status.ERROR;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;

import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockserver.client.MockServerClient;
import org.olf.dcb.core.interaction.sierra.SierraBibsAPIFixture;
import org.olf.dcb.core.interaction.sierra.SierraItemsAPIFixture;
import org.olf.dcb.core.interaction.sierra.SierraPatronsAPIFixture;
import org.olf.dcb.core.model.DataAgency;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.PatronRequest.Status;
import org.olf.dcb.core.model.ReferenceValueMapping;
import org.olf.dcb.core.model.ShelvingLocation;
import org.olf.dcb.request.workflow.PlacePatronRequestAtBorrowingAgencyStateTransition;
import org.olf.dcb.storage.AgencyRepository;
import org.olf.dcb.storage.ShelvingLocationRepository;
import org.olf.dcb.test.BibRecordFixture;
import org.olf.dcb.test.ClusterRecordFixture;
import org.olf.dcb.test.HostLmsFixture;
import org.olf.dcb.test.PatronFixture;
import org.olf.dcb.test.PatronRequestsFixture;
import org.olf.dcb.test.ReferenceValueMappingFixture;
import org.olf.dcb.test.SupplierRequestsFixture;

import io.micronaut.core.io.ResourceLoader;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import jakarta.inject.Inject;
import reactor.core.publisher.Mono;
import services.k_int.interaction.sierra.SierraTestUtils;
import services.k_int.interaction.sierra.bibs.BibPatch;
import services.k_int.test.mockserver.MockServerMicronautTest;

@MockServerMicronautTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PlaceRequestAtBorrowingAgencyTests {
	private static final String HOST_LMS_CODE = "borrowing-agency-service-tests";

	@Inject
	private ResourceLoader loader;

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
	private SupplierRequestsFixture supplierRequestsFixture;
	@Inject
	private ReferenceValueMappingFixture referenceValueMappingFixture;

	private SierraPatronsAPIFixture sierraPatronsAPIFixture;

	@Inject
	private PlacePatronRequestAtBorrowingAgencyStateTransition placePatronRequestAtBorrowingAgencyStateTransition;

	@Inject
	private ShelvingLocationRepository shelvingLocationRepository;
	@Inject
	private AgencyRepository agencyRepository;

	@BeforeAll
	public void beforeAll(MockServerClient mock) {
		final String TOKEN = "test-token";
		final String BASE_URL = "https://borrowing-agency-service-tests.com";
		final String KEY = "borrowing-agency-service-key";
		final String SECRET = "borrowing-agency-service-secret";

		SierraTestUtils.mockFor(mock, BASE_URL)
			.setValidCredentials(KEY, SECRET, TOKEN, 60);

		hostLmsFixture.deleteAllHostLMS();

		hostLmsFixture.createSierraHostLms(KEY, SECRET, BASE_URL, HOST_LMS_CODE);

		this.sierraPatronsAPIFixture = new SierraPatronsAPIFixture(mock, loader);

		final var sierraItemsAPIFixture = new SierraItemsAPIFixture(mock, loader);
		final var sierraBibsAPIFixture = new SierraBibsAPIFixture(mock, loader);

		final var bibPatch = BibPatch.builder()
			.authors(new String[]{"Stafford Beer"})
			.titles(new String[]{"Brain of the Firm"})
			// .bibCode3("n")
			.build();

		sierraBibsAPIFixture.createPostBibsMock(bibPatch, 7916920);
		sierraItemsAPIFixture.successResponseForCreateItem(7916920, "ab6", "9849123490");

		sierraBibsAPIFixture.createPostBibsMock(bibPatch, 7916921);
		sierraItemsAPIFixture.successResponseForCreateItem(7916921, "ab6", "9849123490");

		sierraPatronsAPIFixture.addPatronGetExpectation(872321L);
	}

	@BeforeEach
	void beforeEach() {
		patronRequestsFixture.deleteAllPatronRequests();

		patronFixture.deleteAllPatrons();

		bibRecordFixture.deleteAllBibRecords();
		clusterRecordFixture.deleteAllClusterRecords();

		// add shelving location
		UUID id1 = randomUUID();
		final var dataHostLms1 = hostLmsFixture.createHostLms(id1, "code");

		UUID id = randomUUID();
		final var dataHostLms2 = hostLmsFixture.createHostLms(id, "code");

		final var dataAgency = singleValueFrom(
			agencyRepository.save(new DataAgency(randomUUID(), "ab6", "name", dataHostLms2)));

		final var shelvingLocation = ShelvingLocation.builder()
			.id(randomUUID())
			.code("ab6")
			.name("name")
			.hostSystem(dataHostLms1)
			.agency(dataAgency)
			.build();

		singleValueFrom(shelvingLocationRepository.save(shelvingLocation));

		final var rvm = ReferenceValueMapping.builder()
			.id(randomUUID())
			.fromCategory("ShelvingLocation")
			.fromContext("borrowing-agency-service-tests")
			.fromValue("ab6")
			.toCategory("AGENCY")
			.toContext("DCB")
			.toValue("ab6")
			.build();

		referenceValueMappingFixture.saveReferenceValueMapping(rvm);
	}

	@AfterAll
	void afterAll() {
		Mono.from(shelvingLocationRepository.deleteByCode("ab6")).block();
		Mono.from(agencyRepository.deleteByCode("ab6")).block();
		hostLmsFixture.deleteAllHostLMS();
		patronRequestsFixture.deleteAll();
	}

	@Test
	void placeRequestAtBorrowingAgencySucceeds() {
		// Arrange
		final var clusterRecordId = randomUUID();
		final var clusterRecord = clusterRecordFixture.createClusterRecord(clusterRecordId);

		final var hostLms = hostLmsFixture.findByCode(HOST_LMS_CODE);
		final var sourceSystemId = hostLms.getId();

		bibRecordFixture.createBibRecord(clusterRecordId, sourceSystemId, "798472", clusterRecord);

		final var patron = patronFixture.savePatron("872321");

		patronFixture.saveIdentity(patron, hostLms, "872321", true, "-");

		final var patronRequestId = randomUUID();
		var patronRequest = PatronRequest.builder()
			.id(patronRequestId)
			.patron(patron)
			.bibClusterId(clusterRecordId)
			.status(Status.REQUEST_PLACED_AT_SUPPLYING_AGENCY)
			.pickupLocationCode("ABC123")
			.build();

		patronRequestsFixture.savePatronRequest(patronRequest);

		supplierRequestsFixture.saveSupplierRequest(randomUUID(), patronRequest, "localItemId",
			"ab6", "9849123490", hostLms.code);

		sierraPatronsAPIFixture.patronHoldRequestResponse("872321");

		// This one is for the borrower side hold - we now match a hold using the note instead of the itemid - so we have to fix up a hold with the
		// correct note containing the patronRequestId
		sierraPatronsAPIFixture.patronHoldResponse("872321",
			"https://sandbox.iii.com/iii/sierra-api/v6/patrons/holds/864902",
			"Consortial Hold. tno="+patronRequest.getId());

		// Act
		final var pr = placeRequestAtBorrowingAgency(patronRequest);

		// Assert
		assertThat("Patron request should not be null", pr, is(notNullValue()));
		assertThat("Status code wasn't expected.", pr.getStatus(), is(Status.REQUEST_PLACED_AT_BORROWING_AGENCY));
		assertThat("Local request id wasn't expected.", pr.getLocalRequestId(), is("864902"));
		assertThat("Local request status wasn't expected.", pr.getLocalRequestStatus(), is("PLACED"));
		assertSuccessfulTransitionAudit(pr);
	}

	@Test
	void shouldFailWhenPlacingRequestInSierraRespondsWithServerError() {
		// Arrange
		final var clusterRecordId = randomUUID();
		final var clusterRecord = clusterRecordFixture.createClusterRecord(clusterRecordId);

		final var hostLms = hostLmsFixture.findByCode(HOST_LMS_CODE);
		final var sourceSystemId = hostLms.getId();

		bibRecordFixture.createBibRecord(clusterRecordId, sourceSystemId, "798472", clusterRecord);

		final var patron = patronFixture.savePatron("972321");

		patronFixture.saveIdentity(patron, hostLms, "972321", true, "-");

		final var patronRequestId = randomUUID();
		var patronRequest = PatronRequest.builder()
			.id(patronRequestId)
			.patron(patron)
			.bibClusterId(clusterRecordId)
			.pickupLocationCode("ABC123")
			.status(Status.REQUEST_PLACED_AT_SUPPLYING_AGENCY)
			.build();

		patronRequestsFixture.savePatronRequest(patronRequest);

		supplierRequestsFixture.saveSupplierRequest(randomUUID(), patronRequest, "localItemId",
			"ab6", "9849123490", hostLms.code);

		sierraPatronsAPIFixture.patronHoldRequestErrorResponse("972321");

		// Act
		final var exception = assertThrows(HttpClientResponseException.class,
			() -> placeRequestAtBorrowingAgency(patronRequest));

		// Assert
		final var expectedMessage = "Internal server error: Invalid configuration - [109 / 0]";

		assertThat(exception.getMessage(), is(expectedMessage));

		final var fetchedPatronRequest = patronRequestsFixture.findById(patronRequest.getId());

		assertThat("Request should have error status afterwards",
			fetchedPatronRequest.getStatus(), is(ERROR));

		assertUnsuccessfulTransitionAudit(fetchedPatronRequest, expectedMessage);
	}

	@Test
	void shouldFailWhenPlacedRequestCannotBeFoundInSierra() {
		// Arrange
		final var clusterRecordId = randomUUID();
		final var clusterRecord = clusterRecordFixture.createClusterRecord(clusterRecordId);

		final var hostLms = hostLmsFixture.findByCode(HOST_LMS_CODE);
		final var sourceSystemId = hostLms.getId();

		bibRecordFixture.createBibRecord(clusterRecordId, sourceSystemId, "798472", clusterRecord);

		final var patron = patronFixture.savePatron("972321");

		patronFixture.saveIdentity(patron, hostLms, "785843", true, "-");

		final var patronRequestId = randomUUID();
		var patronRequest = PatronRequest.builder()
			.id(patronRequestId)
			.patron(patron)
			.bibClusterId(clusterRecordId)
			.pickupLocationCode("ABC123")
			.status(Status.REQUEST_PLACED_AT_SUPPLYING_AGENCY)
			.build();

		patronRequestsFixture.savePatronRequest(patronRequest);

		supplierRequestsFixture.saveSupplierRequest(randomUUID(), patronRequest, "localItemId",
			"ab6", "9849123490", hostLms.code);

		sierraPatronsAPIFixture.patronHoldRequestResponse("785843");

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

	private void assertSuccessfulTransitionAudit(PatronRequest patronRequest) {
		final var fetchedAudit = patronRequestsFixture
			.findAuditByPatronRequest(patronRequest).blockFirst();

		assertThat("Patron Request audit should NOT have brief description",
			fetchedAudit.getBriefDescription(), is(nullValue()));

		assertThat("Patron Request audit should have from state",
			fetchedAudit.getFromStatus(), is(Status.REQUEST_PLACED_AT_SUPPLYING_AGENCY));

		assertThat("Patron Request audit should have to state",
			fetchedAudit.getToStatus(), is(Status.REQUEST_PLACED_AT_BORROWING_AGENCY));
	}

	private void assertUnsuccessfulTransitionAudit(PatronRequest patronRequest, String description) {
		final var fetchedAudit = patronRequestsFixture
			.findAuditByPatronRequest(patronRequest).blockFirst();

		assertThat("Patron Request audit should have brief description",
			fetchedAudit.getBriefDescription(), is(description));

		assertThat("Patron Request audit should have from state",
			fetchedAudit.getFromStatus(), is(Status.REQUEST_PLACED_AT_SUPPLYING_AGENCY));

		assertThat("Patron Request audit should have to state",
			fetchedAudit.getToStatus(), is(ERROR));
	}

	private PatronRequest placeRequestAtBorrowingAgency(PatronRequest patronRequest) {
		return placePatronRequestAtBorrowingAgencyStateTransition.attempt(patronRequest).block();
	}
}
