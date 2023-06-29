package org.olf.reshare.dcb.request.fulfilment;

import io.micronaut.core.io.ResourceLoader;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import jakarta.inject.Inject;
import org.junit.jupiter.api.*;
import org.mockserver.client.MockServerClient;
import org.olf.reshare.dcb.core.interaction.sierra.SierraBibsAPIFixture;
import org.olf.reshare.dcb.core.interaction.sierra.SierraItemsAPIFixture;
import org.olf.reshare.dcb.core.interaction.sierra.SierraPatronsAPIFixture;
import org.olf.reshare.dcb.core.model.DataAgency;
import org.olf.reshare.dcb.core.model.DataHostLms;
import org.olf.reshare.dcb.core.model.PatronRequest;
import org.olf.reshare.dcb.core.model.ShelvingLocation;
import org.olf.reshare.dcb.storage.AgencyRepository;
import org.olf.reshare.dcb.storage.ShelvingLocationRepository;
import org.olf.reshare.dcb.test.*;
import reactor.core.publisher.Mono;
import services.k_int.interaction.sierra.SierraTestUtils;
import services.k_int.interaction.sierra.bibs.BibPatch;
import services.k_int.test.mockserver.MockServerMicronautTest;

import java.util.UUID;

import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.olf.reshare.dcb.request.fulfilment.PatronRequestStatusConstants.REQUEST_PLACED_AT_BORROWING_AGENCY;

@MockServerMicronautTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BorrowingAgencyServiceTests {
	private static final String HOST_LMS_CODE = "borrowing-agency-service-tests";

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
	private BorrowingAgencyService borrowingAgencyService;
	@Inject
	private BibRecordFixture bibRecordFixture;
	@Inject
	private SupplierRequestsFixture supplierRequestsFixture;
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

		final var sierraPatronsAPIFixture = new SierraPatronsAPIFixture(mock, loader);
		final var sierraItemsAPIFixture = new SierraItemsAPIFixture(mock, loader);
		final var sierraBibsAPIFixture = new SierraBibsAPIFixture(mock, loader);

		final var bibPatch = BibPatch.builder()
			.authors(new String[]{"Stafford Beer"})
			.titles(new String[]{"Brain of the Firm"})
			.bibCode3("n")
			.build();

		sierraBibsAPIFixture.createPostBibsMock(bibPatch, 7916920);
		sierraItemsAPIFixture.successResponseForCreateItem(7916920, "ab6", "9849123490");
		sierraPatronsAPIFixture.patronHoldRequestResponse("872321", 7916922, "ABC123");
		sierraPatronsAPIFixture.patronHoldResponse("872321");

		sierraBibsAPIFixture.createPostBibsMock(bibPatch, 7916921);
		sierraItemsAPIFixture.successResponseForCreateItem(7916921, "ab6", "9849123490");
		sierraPatronsAPIFixture.patronHoldRequestResponse("43546", 7916922, "ABC123");
		sierraPatronsAPIFixture.patronHoldResponse("43546");

		sierraPatronsAPIFixture.patronHoldRequestErrorResponse("972321", 7916922, "ABC123");

		// Register an expectation that when the client calls /patrons/43546 we respond with the patron record
		sierraPatronsAPIFixture.addPatronGetExpectation(43546L);
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
		DataHostLms dataHostLms1 = hostLmsFixture.createHostLms(id1, "code");
		UUID id = randomUUID();
		DataHostLms dataHostLms2 = hostLmsFixture.createHostLms(id, "code");

		DataAgency dataAgency = Mono.from(
			agencyRepository.save(new DataAgency(randomUUID(), "ab6", "name", dataHostLms2))).block();

		ShelvingLocation shelvingLocation = ShelvingLocation.builder()
			.id(randomUUID())
			.code("ab6")
			.name("name")
			.hostSystem(dataHostLms1)
			.agency(dataAgency)
			.build();

		Mono.from(shelvingLocationRepository.save(shelvingLocation))
			.block();
	}

	@AfterAll
	void afterAll() {
		Mono.from(shelvingLocationRepository.deleteByCode("ab6")).block();
		Mono.from(agencyRepository.deleteByCode("ab6")).block();
		hostLmsFixture.deleteAllHostLMS();
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
		patronFixture.saveIdentity(patron, hostLms, "872321", true);

		final var patronRequestId = randomUUID();
		var patronRequest = PatronRequest.builder()
			.id(patronRequestId)
			.patron(patron)
			.bibClusterId(clusterRecordId)
			.pickupLocationCode("ABC123")
			.build();

		patronRequestsFixture.savePatronRequest(patronRequest);
		supplierRequestsFixture.saveSupplierRequest(randomUUID(), patronRequest, "localItemId",
			"ab6", "9849123490", hostLms.code);

		// Act
		final var pr = borrowingAgencyService.placePatronRequestAtBorrowingAgency(patronRequest).block();

		// Assert
		assertThat("Patron request should not be null", pr, is(notNullValue()));
		assertThat("Status code wasn't expected.", pr.getStatusCode(), is(REQUEST_PLACED_AT_BORROWING_AGENCY));
		assertThat("Local request id wasn't expected.", pr.getLocalRequestId(), is("864902"));
		assertThat("Local request status wasn't expected.", pr.getLocalRequestStatus(), is("PLACED"));
	}

	@Test
	void placeRequestAtBorrowingAgencyReturns500response() {
		// Arrange
		final var clusterRecordId = randomUUID();
		final var clusterRecord = clusterRecordFixture.createClusterRecord(clusterRecordId);

		final var hostLms = hostLmsFixture.findByCode(HOST_LMS_CODE);
		final var sourceSystemId = hostLms.getId();

		bibRecordFixture.createBibRecord(clusterRecordId, sourceSystemId, "798472", clusterRecord);

		final var patron = patronFixture.savePatron("972321");

		patronFixture.saveIdentity(patron, hostLms, "972321", true);

		final var patronRequestId = randomUUID();
		var patronRequest = PatronRequest.builder()
			.id(patronRequestId)
			.patron(patron)
			.bibClusterId(clusterRecordId)
			.pickupLocationCode("ABC123")
			.build();

		patronRequestsFixture.savePatronRequest(patronRequest);
		supplierRequestsFixture.saveSupplierRequest(randomUUID(), patronRequest, "localItemId",
			"ab6", "9849123490", hostLms.code);

		// Act
		final var exception = assertThrows(HttpClientResponseException.class,
			() -> borrowingAgencyService.placePatronRequestAtBorrowingAgency(patronRequest).block());

		// Assert
		assertThat(exception.getMessage(), is("Internal Server Error"));
		assertThat(exception.getLocalizedMessage(), is("Internal Server Error"));

		final var fetchedPatronRequest = patronRequestsFixture.findById(patronRequest.getId());

		assertThat("Request should have error status afterwards",
			fetchedPatronRequest.getStatusCode(), is("ERROR"));
	}
}
