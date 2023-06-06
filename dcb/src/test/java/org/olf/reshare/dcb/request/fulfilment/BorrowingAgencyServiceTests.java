package org.olf.reshare.dcb.request.fulfilment;

import io.micronaut.context.annotation.Property;
import io.micronaut.core.io.ResourceLoader;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import lombok.SneakyThrows;
import org.junit.jupiter.api.*;
import org.mockserver.client.MockServerClient;
import org.olf.reshare.dcb.core.HostLmsService;
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

import static io.micronaut.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.olf.reshare.dcb.request.fulfilment.PatronRequestStatusConstants.REQUEST_PLACED_AT_BORROWING_AGENCY;


@MockServerMicronautTest
@MicronautTest(transactional = false, propertySources = { "classpath:configs/PatronRequestApiTests.yml" }, rebuildContext = true)
@Property(name = "r2dbc.datasources.default.options.maxSize", value = "1")
@Property(name = "r2dbc.datasources.default.options.initialSize", value = "1")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BorrowingAgencyServiceTests {
	private static final String SIERRA_TOKEN = "test-token-for-user";

	@Inject
	ResourceLoader loader;

	@Inject
	private HostLmsFixture hostLmsFixture;

	@Inject
	private PatronRequestsFixture patronRequestsFixture;

	@Inject
	private PatronFixture patronFixture;

	@Inject
	private HostLmsService hostLmsService;

	@Inject
	private ClusterRecordFixture clusterRecordFixture;


	@Inject
	private BibRecordFixture bibRecordFixture;

	@Inject
	private PatronIdentityFixture patronIdentityFixture;

	@Inject
	private SupplierRequestsFixture supplierRequestsFixture;
	@Inject
	private BorrowingAgencyService borrowingAgencyService;

	@Inject
	private ShelvingLocationRepository shelvingLocationRepository;

	@Inject
	private AgencyRepository agencyRepository;

	// Properties should line up with included property source for the spec.
	@Property(name = "hosts.test1.client.base-url")
	private String sierraHost;

	@Property(name = "hosts.test1.client.key")
	private String sierraUser;

	@Property(name = "hosts.test1.client.secret")
	private String sierraPass;

	@BeforeAll
	@SneakyThrows
	public void addFakeSierraApis(MockServerClient mock) {
		SierraTestUtils.mockFor(mock, sierraHost)
			.setValidCredentials(sierraUser, sierraPass, SIERRA_TOKEN, 60);

		final var sierraPatronsAPIFixture = new SierraPatronsAPIFixture(mock, loader);
		final var sierraItemsAPIFixture = new SierraItemsAPIFixture(mock, loader);
		final var sierraBibsAPIFixture = new SierraBibsAPIFixture(mock, loader);

		BibPatch bibPatch = BibPatch.builder()
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
		DataHostLms dataHostLms1 = hostLmsFixture.createHostLms_returnDataHostLms(randomUUID(), "code");
		DataHostLms dataHostLms2 = hostLmsFixture.createHostLms_returnDataHostLms(randomUUID(), "code");

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

		final var testHostLms = hostLmsService.findByCode("test1").block();
		final var sourceSystemId = testHostLms.getId();

		bibRecordFixture.createBibRecord(clusterRecordId, sourceSystemId, "798472", clusterRecord);

		final var patron = patronFixture.savePatron(randomUUID(), "872321");
		patronIdentityFixture.saveHomeIdentity(randomUUID(), patron, "872321", testHostLms);

		final var patronRequestId = randomUUID();
		var patronRequest = PatronRequest.builder()
			.id(patronRequestId)
			.patron(patron)
			.bibClusterId(clusterRecordId)
			.pickupLocationCode("ABC123")
			.build();

		patronRequestsFixture.savePatronRequest(patronRequest);
		supplierRequestsFixture.saveSupplierRequest(randomUUID(), patronRequest, "localItemId",
			"ab6", "9849123490", testHostLms.code);

		// Act
		final var pr = borrowingAgencyService.placePatronRequestAtBorrowingAgency(patronRequest).block();

		// Assert
		assertThat("Status code wasn't expected.", pr.getStatusCode(), is(REQUEST_PLACED_AT_BORROWING_AGENCY));
		assertThat("Local request id wasn't expected.", pr.getLocalRequestId(), is("864902"));
		assertThat("Local request status wasn't expected.", pr.getLocalRequestStatus(), is("PLACED"));
	}

	@Test
	void placeRequestAtBorrowingAgencyThrows500WithBodyBroken() {
		// Arrange
		final var clusterRecordId = randomUUID();
		final var clusterRecord = clusterRecordFixture.createClusterRecord(clusterRecordId);

		final var testHostLms = hostLmsService.findByCode("test1").block();
		final var sourceSystemId = testHostLms.getId();

		bibRecordFixture.createBibRecord(clusterRecordId, sourceSystemId, "798472", clusterRecord);

		final var patron = patronFixture.savePatron(randomUUID(), "972321");
		patronIdentityFixture.saveHomeIdentity(randomUUID(), patron, "972321", testHostLms);

		final var patronRequestId = randomUUID();
		var patronRequest = PatronRequest.builder()
			.id(patronRequestId)
			.patron(patron)
			.bibClusterId(clusterRecordId)
			.pickupLocationCode("ABC123")
			.build();

		patronRequestsFixture.savePatronRequest(patronRequest);
		supplierRequestsFixture.saveSupplierRequest(randomUUID(), patronRequest, "localItemId",
			"ab6", "9849123490", testHostLms.code);

		// Act
		final var exception = assertThrows(HttpClientResponseException.class,
			() -> borrowingAgencyService.placePatronRequestAtBorrowingAgency(patronRequest).block());

		// Assert
		assertThat(exception, is(notNullValue()));
		final var response = exception.getResponse();
		assertThat(response.getStatus(), is(INTERNAL_SERVER_ERROR));
		assertThat(response.code(), is(500));
		assertThat(response.getBody(String.class).get(), is("Broken"));
	}
}
