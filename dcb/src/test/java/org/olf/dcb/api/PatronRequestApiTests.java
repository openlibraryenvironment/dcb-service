package org.olf.dcb.api;

import static io.micronaut.http.HttpStatus.BAD_REQUEST;
import static io.micronaut.http.HttpStatus.NOT_FOUND;
import static io.micronaut.http.HttpStatus.OK;
import static java.util.Objects.requireNonNull;
import static java.util.UUID.randomUUID;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.olf.dcb.core.model.PatronRequest.Status.PATRON_VERIFIED;
import static org.olf.dcb.core.model.PatronRequest.Status.REQUEST_PLACED_AT_BORROWING_AGENCY;
import static org.olf.dcb.core.model.PatronRequest.Status.REQUEST_PLACED_AT_SUPPLYING_AGENCY;
import static org.olf.dcb.core.model.PatronRequest.Status.RESOLVED;

import java.util.List;
import java.util.UUID;

import io.micronaut.http.client.BlockingHttpClient;
import io.micronaut.security.authentication.UsernamePasswordCredentials;
import io.micronaut.security.token.render.BearerAccessRefreshToken;
import org.hamcrest.Matcher;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockserver.client.MockServerClient;
import org.olf.dcb.core.interaction.sierra.SierraBibsAPIFixture;
import org.olf.dcb.core.interaction.sierra.SierraItemsAPIFixture;
import org.olf.dcb.core.interaction.sierra.SierraPatronsAPIFixture;
import org.olf.dcb.core.model.DataAgency;
import org.olf.dcb.core.model.DataHostLms;
import org.olf.dcb.core.model.ReferenceValueMapping;
import org.olf.dcb.core.model.ShelvingLocation;
import org.olf.dcb.storage.AgencyRepository;
import org.olf.dcb.storage.ShelvingLocationRepository;
import org.olf.dcb.test.AgencyFixture;
import org.olf.dcb.test.BibRecordFixture;
import org.olf.dcb.test.ClusterRecordFixture;
import org.olf.dcb.test.HostLmsFixture;
import org.olf.dcb.test.PatronFixture;
import org.olf.dcb.test.PatronRequestsFixture;
import org.olf.dcb.test.ReferenceValueMappingFixture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micronaut.core.io.ResourceLoader;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import jakarta.inject.Inject;
import net.minidev.json.JSONObject;
import reactor.core.publisher.Mono;
import services.k_int.interaction.sierra.SierraTestUtils;
import services.k_int.interaction.sierra.bibs.BibPatch;
import services.k_int.test.mockserver.MockServerMicronautTest;

@MockServerMicronautTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PatronRequestApiTests {
	private final Logger log = LoggerFactory.getLogger(PatronRequestApiTests.class);

	private static final String HOST_LMS_CODE = "patron-request-api-tests";

	@Inject
	ResourceLoader loader;
	@Inject
	private PatronRequestsFixture patronRequestsFixture;
	@Inject
	private PatronFixture patronFixture;
	@Inject
	private HostLmsFixture hostLmsFixture;
	@Inject
	private ClusterRecordFixture clusterRecordFixture;
	@Inject
	private BibRecordFixture bibRecordFixture;
	@Inject
	private PatronRequestApiClient patronRequestApiClient;
	@Inject
	private ShelvingLocationRepository shelvingLocationRepository;
	@Inject
	private AgencyRepository agencyRepository;
	@Inject
	private AdminApiClient adminApiClient;
	@Inject
	private ReferenceValueMappingFixture referenceValueMappingFixture;
	@Inject
	private AgencyFixture agencyFixture;

	private SierraPatronsAPIFixture sierraPatronsAPIFixture;

	@Inject
	@Client("/")
	private HttpClient client;

	@BeforeAll
	void beforeAll(MockServerClient mock) {
		final String TOKEN = "test-token";
		final String BASE_URL = "https://patron-request-api-tests.com";
		final String KEY = "patron-request-key";
		final String SECRET = "patron-request-secret";

		SierraTestUtils.mockFor(mock, BASE_URL).setValidCredentials(KEY, SECRET, TOKEN, 60);

		hostLmsFixture.deleteAll();

		DataHostLms h1 = hostLmsFixture.createSierraHostLms(KEY, SECRET, BASE_URL, HOST_LMS_CODE);
                log.debug("Created dataHostLms {}",h1);

		final var sierraItemsAPIFixture = new SierraItemsAPIFixture(mock, loader);
		// Moved to class level var so we can install fixtures elsewhere
		// final var sierraPatronsAPIFixture = new SierraPatronsAPIFixture(mock,
		// loader);
		this.sierraPatronsAPIFixture = new SierraPatronsAPIFixture(mock, loader);
		final var sierraBibsAPIFixture = new SierraBibsAPIFixture(mock, loader);

		sierraItemsAPIFixture.twoItemsResponseForBibId("798472");
		sierraItemsAPIFixture.zeroItemsResponseForBibId("565382");

		// patron service
//		sierraPatronsAPIFixture.patronNotFoundResponseForUniqueId("872321@home-library");
		sierraPatronsAPIFixture.patronNotFoundResponseForUniqueId("u", "872321@ab6");

//		sierraPatronsAPIFixture.postPatronResponse("872321@home-library", 2745326);
		 sierraPatronsAPIFixture.postPatronResponse("872321@ab6", 2745326);

		// supplying agency service
		sierraPatronsAPIFixture.patronHoldRequestResponse("2745326");

		// borrowing agency service
		final var bibPatch = BibPatch.builder()
			.authors(List.of("Stafford Beer"))
			.titles(List.of("Brain of the Firm"))
			.build();

		sierraBibsAPIFixture.createPostBibsMock(bibPatch, 7916920);
		sierraItemsAPIFixture.successResponseForCreateItem(7916920, "ab6", "6565750674");
		sierraPatronsAPIFixture.patronHoldRequestResponse("872321");

		sierraBibsAPIFixture.createPostBibsMock(bibPatch, 7916921);
		sierraItemsAPIFixture.successResponseForCreateItem(7916921, "ab6", "9849123490");

		agencyFixture.deleteAllAgencies();
		DataAgency da = agencyFixture.saveAgency(DataAgency.builder()
			.id(UUID.randomUUID())
			.code("AGENCY1")
			.name("Test AGENCY1")
			.hostLms(h1)
			.build());

                log.debug("Create dataAgency {}",da);

		sierraPatronsAPIFixture.addPatronGetExpectation("43546");
		sierraPatronsAPIFixture.addPatronGetExpectation("872321");
	}

	@BeforeEach
	void beforeEach() {
		patronRequestsFixture.deleteAllPatronRequests();

		patronFixture.deleteAllPatrons();

		bibRecordFixture.deleteAllBibRecords();
		clusterRecordFixture.deleteAllClusterRecords();

		referenceValueMappingFixture.deleteAllReferenceValueMappings();


                log.debug("Creating dataHostLms records for codeAA and codeBB");
		// add shelving location
		UUID id1 = randomUUID();
		DataHostLms dataHostLms1 = hostLmsFixture.createHostLms(id1, "codeAA");

		UUID id = randomUUID();
		DataHostLms dataHostLms2 = hostLmsFixture.createHostLms(id, "codeBB");

                log.debug("Creating dataAgency record for ab6");
		DataAgency dataAgency = Mono.from(agencyRepository.save(
			DataAgency.builder().id(randomUUID()).code("ab6").name("name").hostLms(dataHostLms2).build()))
                        .doOnSuccess(da -> log.debug("Created ab6"))
                        .doOnError(err -> log.error("Failure to create ab6 data agency {}",err))
			.block();

		ShelvingLocation shelvingLocation = ShelvingLocation.builder().id(randomUUID()).code("ab6").name("name")
				.hostSystem(dataHostLms1).agency(dataAgency).build();

		Mono.from(shelvingLocationRepository.save(shelvingLocation)).block();

		ReferenceValueMapping pul = ReferenceValueMapping.builder().id(randomUUID()).fromCategory("PickupLocation")
				.fromContext("DCB").fromValue("ABC123").toCategory("AGENCY").toContext("DCB").toValue("AGENCY1")
				.build();
		referenceValueMappingFixture.saveReferenceValueMapping(pul);
        
		ReferenceValueMapping rvm = ReferenceValueMapping.builder().id(randomUUID()).fromCategory("ShelvingLocation")
				.fromContext("patron-request-api-tests").fromValue("ab6").toCategory("AGENCY").toContext("DCB").toValue("ab6")
				.build();
		referenceValueMappingFixture.saveReferenceValueMapping(rvm);

                ReferenceValueMapping rvm2= ReferenceValueMapping.builder().id(randomUUID()).fromCategory("Location")
                                .fromContext("patron-request-api-tests").fromValue("tstce").toCategory("AGENCY").toContext("DCB").toValue("ab6")
                                .build();
                referenceValueMappingFixture.saveReferenceValueMapping(rvm2);

                ReferenceValueMapping rvm3= ReferenceValueMapping.builder().id(randomUUID()).fromCategory("Location")
                                .fromContext("patron-request-api-tests").fromValue("tstr").toCategory("AGENCY").toContext("DCB").toValue("ab6")
                                .build();
                referenceValueMappingFixture.saveReferenceValueMapping(rvm3);
		// Mono.from(referenceValueMappingRepository.save(rvm))
		// .block();

	}

	@AfterAll
	void afterAll() {
		Mono.from(shelvingLocationRepository.deleteByCode("ab6")).block();
		Mono.from(agencyRepository.deleteByCode("ab6")).block();
		hostLmsFixture.deleteAll();
	}

	@Test
	@DisplayName("should be able to place patron request for new patron")
	void shouldBeAbleToPlacePatronForNewPatron() {
		log.info("\n\nshouldBeAbleToPlacePatronForNewPatron\n\n");
		// Arrange
		final var clusterRecordId = randomUUID();
		final var clusterRecord = clusterRecordFixture.createClusterRecord(clusterRecordId);
		final var hostLms = hostLmsFixture.findByCode(HOST_LMS_CODE);
		final var sourceSystemId = hostLms.getId();
		savePatronTypeMappings();

		bibRecordFixture.createBibRecord(clusterRecordId, sourceSystemId, "798472", clusterRecord);

		// Act
		var placedRequestResponse = patronRequestApiClient.placePatronRequest(clusterRecordId, "872321", "ABC123",
				HOST_LMS_CODE, "home-library");

		// Assert
		assertThat(placedRequestResponse.getStatus(), is(OK));

		final var placedPatronRequest = placedRequestResponse.body();

		assertThat(placedPatronRequest, is(notNullValue()));

		// Fix up the sierra mock so that it finds a hold with the right note in it
		// 2745326 will be the identity of this patron in the supplier side system
		log.info("Inserting hold response for patron 2745326 - placedPatronRequest.id=" + placedPatronRequest.id());
		sierraPatronsAPIFixture.patronHoldResponse("2745326",
				"https://sandbox.iii.com/iii/sierra-api/v6/patrons/holds/407557",
				"Consortial Hold. tno=" + placedPatronRequest.id());

		// This one is for the borrower side hold
		sierraPatronsAPIFixture.patronHoldResponse("872321",
				"https://sandbox.iii.com/iii/sierra-api/v6/patrons/holds/864902",
				"Consortial Hold. tno=" + placedPatronRequest.id());

		// We need to take the placedRequestResponse and somehow inject it's ID into the
		// patronHolds respons message as note="Consortial Hold. tno=UUID"
		// This will ensure that the subsequent lookup can correlate the hold with the
		// request
		// maybe something like sierraPatronsAPIFixture.patronHoldResponse("872321",
		// placedRequestResponse.id);

		assertThat(placedPatronRequest.requestor(), is(notNullValue()));
		assertThat(placedPatronRequest.requestor().homeLibraryCode(), is("home-library"));
		assertThat(placedPatronRequest.requestor().localSystemCode(), is(HOST_LMS_CODE));
		assertThat(placedPatronRequest.requestor().localId(), is("872321"));

		log.info("Waiting for placed....");
		AdminApiClient.AdminAccessPatronRequest fetchedPatronRequest = await().atMost(8, SECONDS)
				.until(() -> adminApiClient.getPatronRequestViaAdminApi(placedPatronRequest.id()), isPlacedAtBorrowingAgency());

		assertThat(fetchedPatronRequest, is(notNullValue()));

		assertThat(fetchedPatronRequest.citation(), is(notNullValue()));
		assertThat(fetchedPatronRequest.citation().bibClusterId(), is(clusterRecordId));

		assertThat(fetchedPatronRequest.pickupLocation(), is(notNullValue()));
		assertThat(fetchedPatronRequest.pickupLocation().code(), is("ABC123"));

		assertThat(fetchedPatronRequest.status(), is(notNullValue()));
		assertThat(fetchedPatronRequest.status().code(), is("REQUEST_PLACED_AT_BORROWING_AGENCY"));
		assertThat(fetchedPatronRequest.status().errorMessage(), is(nullValue()));

		assertThat(fetchedPatronRequest.localRequest().id(), is("864902"));
		assertThat(fetchedPatronRequest.localRequest().status(), is("PLACED"));
		assertThat(fetchedPatronRequest.localRequest().itemId(), is("7916922"));
		assertThat(fetchedPatronRequest.localRequest().bibId(), is("7916920"));

		assertThat(fetchedPatronRequest.supplierRequests(), hasSize(1));

		assertThat(fetchedPatronRequest.requestor(), is(notNullValue()));
		assertThat(fetchedPatronRequest.requestor().homeLibraryCode(), is("home-library"));

		assertThat(fetchedPatronRequest.requestor().identities(), is(notNullValue()));
		assertThat(fetchedPatronRequest.requestor().identities(), hasSize(2));

                // The order can change depending upon access, so force the order so that the get(n) below work as expected
                // Collections.sort(fetchedPatronRequest.requestor().identities(), (i1, i2) -> { return i1.localId().compareTo(i2.localId()); });

		final var homeIdentity = fetchedPatronRequest.requestor().identities().get(1);

		assertThat(homeIdentity.localId(), is("872321"));
		assertThat(homeIdentity.homeIdentity(), is(true));
		assertThat(homeIdentity.hostLmsCode(), is(HOST_LMS_CODE));

		final var supplierIdentity = fetchedPatronRequest.requestor().identities().get(0);

		assertThat(supplierIdentity.localId(), is("2745326"));
		assertThat(supplierIdentity.homeIdentity(), is(false));
		assertThat(supplierIdentity.hostLmsCode(), is(HOST_LMS_CODE));

		final var supplierRequest = fetchedPatronRequest.supplierRequests().get(0);

		assertThat(supplierRequest.id(), is(notNullValue()));
		assertThat(supplierRequest.hostLmsCode(), is(HOST_LMS_CODE));
		assertThat(supplierRequest.status(), is("PLACED"));
		assertThat(supplierRequest.localHoldId(), is("407557"));
		assertThat(supplierRequest.localHoldStatus(), is("PLACED"));
		assertThat(supplierRequest.item().id(), is("1000002"));
		assertThat(supplierRequest.item().localItemBarcode(), is("6565750674"));
		assertThat(supplierRequest.item().localItemLocationCode(), is("ab6"));

		assertThat(fetchedPatronRequest.audits(), is(notNullValue()));

		final var lastAuditValue = fetchedPatronRequest.audits().size();
		final var lastAudit = fetchedPatronRequest.audits().get(lastAuditValue - 1);

		assertThat(lastAudit.patronRequestId(), is(fetchedPatronRequest.id().toString()));
		assertThat(lastAudit.description(), is(nullValue()));
		assertThat(lastAudit.fromStatus(), is(REQUEST_PLACED_AT_SUPPLYING_AGENCY));
		assertThat(lastAudit.toStatus(), is(REQUEST_PLACED_AT_BORROWING_AGENCY));
		assertThat(lastAudit.date(), is(notNullValue()));
	}

	@Test
	void cannotFulfilPatronRequestWhenNoRequestableItemsAreFound() {
		log.info("\n\ncannotFulfilPatronRequestWhenNoRequestableItemsAreFound\n\n");
		// Arrange
		final var clusterRecordId = randomUUID();
		final var clusterRecord = clusterRecordFixture.createClusterRecord(clusterRecordId);
		final var hostLms = hostLmsFixture.findByCode(HOST_LMS_CODE);
		final var sourceSystemId = hostLms.getId();

		bibRecordFixture.createBibRecord(clusterRecordId, sourceSystemId, "565382", clusterRecord);

		// Act
		final var placedRequestResponse = patronRequestApiClient.placePatronRequest(clusterRecordId, "43546", "ABC123",
				HOST_LMS_CODE, "homeLibraryCode");

		// Need a longer timeout because retrying the Sierra API,
		// which happens when the zero items 404 response is received,
		// takes longer than success
		final var fetchedPatronRequest = await().atMost(12, SECONDS).until(
				() -> adminApiClient.getPatronRequestViaAdminApi(requireNonNull(placedRequestResponse.body()).id()),
				isNotAvailableToRequest());

		// Assert
		assertThat(placedRequestResponse.getStatus(), is(OK));

		assertThat(fetchedPatronRequest, is(notNullValue()));
		assertThat(fetchedPatronRequest.citation().bibClusterId(), is(clusterRecordId));
		assertThat(fetchedPatronRequest.pickupLocation().code(), is("ABC123"));
		assertThat(fetchedPatronRequest.status().code(), is("NO_ITEMS_AVAILABLE_AT_ANY_AGENCY"));
		assertThat(fetchedPatronRequest.status().errorMessage(), is(nullValue()));
		assertThat(fetchedPatronRequest.requestor().identities(), hasSize(1));

		final var homeIdentity = fetchedPatronRequest.requestor().identities().get(0);
		assertThat(homeIdentity.homeIdentity(), is(true));
		assertThat(homeIdentity.hostLmsCode(), is(HOST_LMS_CODE));
		assertThat(homeIdentity.localId(), is("43546"));

		// No supplier request
		assertThat(fetchedPatronRequest.supplierRequests(), is(nullValue()));

		assertThat(fetchedPatronRequest.audits(), is(notNullValue()));

		final var lastAuditValue = fetchedPatronRequest.audits().size();
		final var lastAudit = fetchedPatronRequest.audits().get(lastAuditValue - 1);

		assertThat(lastAudit.patronRequestId(), is(fetchedPatronRequest.id().toString()));
		assertThat(lastAudit.description(), is(nullValue()));
		assertThat(lastAudit.fromStatus(), is(PATRON_VERIFIED));
		assertThat(lastAudit.toStatus(), is(RESOLVED));
		assertThat(lastAudit.date(), is(notNullValue()));
	}

	@Test
	void cannotPlaceRequestWhenNoInformationIsProvided() {
		log.info("\n\ncannotPlaceRequestWhenNoInformationIsProvided\n\n");
		// Given an empty request body
		final var requestBody = new JSONObject();
		final var request = HttpRequest.POST("/patrons/requests/place", requestBody);

		// When placing a request without providing any information
		final var exception = assertThrows(HttpClientResponseException.class, () -> client.toBlocking().exchange(request));

		// Then a bad request response should be returned
		final var response = exception.getResponse();
		assertThat("Should return a bad request status", response.getStatus(), is(BAD_REQUEST));
	}

	@Test
	void cannotPlaceRequestForPatronAtUnknownLocalSystem() {
		log.info("\n\ncannotPlaceRequestForPatronAtUnknownLocalSystem\n\n");
		// Arrange
		final var clusterRecordId = randomUUID();
		final var clusterRecord = clusterRecordFixture.createClusterRecord(clusterRecordId);
		final var hostLms = hostLmsFixture.findByCode(HOST_LMS_CODE);
		final var sourceSystemId = hostLms.getId();

		bibRecordFixture.createBibRecord(clusterRecordId, sourceSystemId, "798472", clusterRecord);

		// Act
		final var requestBody = new JSONObject() {
			{
				put("citation", new JSONObject() {
					{
						put("bibClusterId", clusterRecordId.toString());
					}
				});
				put("requestor", new JSONObject() {
					{
						put("localId", "73825");
						put("localSystemCode", "unknown-system");
						put("homeLibraryCode", "home-library-code");
					}
				});
				put("pickupLocation", new JSONObject() {
					{
						put("code", "ABC123");
					}
				});
			}
		};

		final var blockingClient = client.toBlocking();
		final var accessToken = getAccessToken(blockingClient);
		final var request = HttpRequest.POST("/patrons/requests/place", requestBody).bearerAuth(accessToken);

		// When placing a request for a patron at an unknown local system
		final var exception = assertThrows(HttpClientResponseException.class, () -> blockingClient.exchange(request));

		// Then a bad request response should be returned
		final var response = exception.getResponse();

		assertThat("Should return a bad request status", response.getStatus(), is(BAD_REQUEST));

		final var optionalBody = response.getBody(String.class);

		assertThat("Response should have a body", optionalBody.isPresent(), is(true));

		assertThat("Body should report no Host LMS found error", optionalBody.get(),
				is("No Host LMS found for code: unknown-system"));
	}

	@Test
	void cannotFindPatronRequestForUnknownId() {
		log.info("\n\ncannotFindPatronRequestForUnknownId\n\n");
		final var exception = assertThrows(HttpClientResponseException.class,
				() -> adminApiClient.getPatronRequestViaAdminApi(randomUUID()));

		final var response = exception.getResponse();

		assertThat(response.getStatus(), is(NOT_FOUND));
	}

	private static Matcher<Object> isPlacedAtBorrowingAgency() {
		return hasProperty("statusCode", is("REQUEST_PLACED_AT_BORROWING_AGENCY"));
	}

	private static Matcher<Object> isNotAvailableToRequest() {
		return hasProperty("statusCode", is("NO_ITEMS_AVAILABLE_AT_ANY_AGENCY"));
	}

	private void savePatronTypeMappings() {
		referenceValueMappingFixture.saveReferenceValueMapping(
				patronFixture.createPatronTypeMapping("patron-request-api-tests", "15", "DCB", "15"));

		referenceValueMappingFixture.saveReferenceValueMapping(
				patronFixture.createPatronTypeMapping("DCB", "15", "patron-request-api-tests", "15"));
	}

	private static String getAccessToken(BlockingHttpClient blockingClient) {
		final var creds = new UsernamePasswordCredentials("admin", "password");
		final var loginRequest = HttpRequest.POST("/login", creds);
		final var loginResponse = blockingClient.exchange(loginRequest, BearerAccessRefreshToken.class);
		final var bearerAccessRefreshToken = loginResponse.body();
		final var accessToken = bearerAccessRefreshToken.getAccessToken();
		return accessToken;
	}
}
