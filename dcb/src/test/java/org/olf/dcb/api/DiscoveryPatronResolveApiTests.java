package org.olf.dcb.api;

import static java.util.UUID.randomUUID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.mockserver.model.HttpResponse.response;
import static org.olf.dcb.security.RoleNames.DISCOVERY_SERVICE;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockserver.client.MockServerClient;
import org.olf.dcb.core.interaction.folio.MockFolioFixture;
import org.olf.dcb.core.interaction.folio.User;
import org.olf.dcb.core.model.DataHostLms;
import org.olf.dcb.security.TestStaticTokenValidator;
import org.olf.dcb.test.AgencyFixture;
import org.olf.dcb.test.HostLmsFixture;
import org.olf.dcb.test.PatronFixture;

import io.micronaut.context.annotation.Property;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import jakarta.inject.Inject;
import services.k_int.test.mockserver.MockServerMicronautTest;

/**
 * An institution sign-in turned into a borrower at that institution's library, over the
 * discovery API. See docs/discovery-service-approach.md section 6.4.
 */
@MockServerMicronautTest
@Property(name = "dcb.discovery.enabled", value = "true")
@Property(name = "dcb.discovery.trusted-services-json", value = """
	[{"service-id": "a-discovery-service", "issuer": "https://discovery.test.invalid",
	  "jwks-uri": "https://discovery.test.invalid/jwks.json", "client-id": "resolve-client"}]""")
@TestInstance(PER_CLASS)
class DiscoveryPatronResolveApiTests {

	private static final String API_KEY = "eyJzIjoic2FsdCIsInQiOiJ0ZW5hbnQiLCJ1IjoidXNlciJ9";
	private static final String ACCESS_TOKEN = "discovery-resolve-token";
	private static final String UNTRUSTED_TOKEN = "discovery-resolve-untrusted-token";
	private static final String HOST_LMS = "discovery-resolve-tests";
	private static final String AGENCY = "resolve-agency";

	@Inject
	@Client("/")
	private HttpClient client;

	@Inject
	private HostLmsFixture hostLmsFixture;

	@Inject
	private AgencyFixture agencyFixture;

	@Inject
	private PatronFixture patronFixture;

	private DataHostLms hostLms;

	private MockFolioFixture mockFolioFixture;

	@BeforeAll
	void beforeAll(MockServerClient mockServerClient) {
		TestStaticTokenValidator.add(ACCESS_TOKEN, "a-discovery-service",
			List.of(DISCOVERY_SERVICE), Map.of("azp", "resolve-client"));
		TestStaticTokenValidator.add(UNTRUSTED_TOKEN, "not-onboarded",
			List.of(DISCOVERY_SERVICE), Map.of("azp", "not-onboarded-client"));

		patronFixture.deleteAllPatrons();
		agencyFixture.deleteAll();
		hostLmsFixture.deleteAll();

		hostLms = hostLmsFixture.createFolioHostLms(HOST_LMS, "https://fake-folio",
			API_KEY, "", "");
		agencyFixture.defineAgency(AGENCY, "Resolve agency", hostLms);

		mockFolioFixture = new MockFolioFixture(mockServerClient, "fake-folio", API_KEY);
	}

	@Test
	void namesTheBorrowerTheSignInMatchesAtTheirLibrary() {
		final var principal = "p-" + randomUUID();
		final var patronId = randomUUID().toString();

		mockFolioFixture.mockGetUsersWithQuery("barcode", principal,
			User.builder().id(patronId).barcode(principal).build());

		final var body = resolve(AGENCY, principal);

		assertThat(body.get("systemCode"), is(HOST_LMS));
		assertThat(body.get("patronId"), is(patronId));
		assertThat(body.get("agencyCode"), is(AGENCY));
		// The identity an assertion needs, and nothing a person could be recognised by.
		assertThat(body.toString(), not(containsString(principal)));
	}

	@Test
	void aSignInTheLibraryDoesNotKnowIsNotFound() {
		final var principal = "p-" + randomUUID();

		mockFolioFixture.mockGetUsersWithQuery("barcode", principal);

		assertThat(statusOf(AGENCY, principal), is(HttpStatus.NOT_FOUND));
	}

	@Test
	void aSignInMatchingAVirtualPatronDcbCreatedThereIsNotFound() {
		final var principal = "p-" + randomUUID();
		final var virtualPatronId = randomUUID().toString();

		final var elsewhere = patronFixture.savePatron("ELSEWHERE");
		patronFixture.saveIdentity(elsewhere, hostLms, virtualPatronId, false, "UNDRGRD", null, null);

		mockFolioFixture.mockGetUsersWithQuery("barcode", principal,
			User.builder().id(virtualPatronId).barcode(principal).build());

		assertThat(statusOf(AGENCY, principal), is(HttpStatus.NOT_FOUND));
	}

	@Test
	void aClientThatIsNotATrustedServiceIsRefused() {
		final var principal = "p-" + randomUUID();

		mockFolioFixture.mockGetUsersWithQuery("barcode", principal,
			User.builder().id(randomUUID().toString()).barcode(principal).build());

		assertThat(statusOf(AGENCY, principal, UNTRUSTED_TOKEN), is(HttpStatus.FORBIDDEN));
	}

	@Test
	void anAgencyDcbDoesNotKnowIsNotFound() {
		assertThat(statusOf("no-such-agency", "anyone"), is(HttpStatus.NOT_FOUND));
	}

	@Test
	void aLibraryThatCannotBeAskedIsNotAnswerForThePatron() {
		final var principal = "p-" + randomUUID();

		mockFolioFixture.mockGetUsersWithQuery("barcode", principal,
			response().withStatusCode(500));

		// Not a 404: "we could not ask" must not read as "no such borrower".
		assertThat(statusOf(AGENCY, principal), is(HttpStatus.BAD_GATEWAY));
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> resolve(String agencyCode, String principal) {
		return client.toBlocking().retrieve(request(agencyCode, principal), Map.class);
	}

	private HttpStatus statusOf(String agencyCode, String principal) {
		return statusOf(agencyCode, principal, ACCESS_TOKEN);
	}

	private HttpStatus statusOf(String agencyCode, String principal, String token) {
		final var failure = assertThrows(HttpClientResponseException.class,
			() -> client.toBlocking().exchange(request(agencyCode, principal, token)));

		return failure.getStatus();
	}

	private HttpRequest<?> request(String agencyCode, String principal) {
		return request(agencyCode, principal, ACCESS_TOKEN);
	}

	private HttpRequest<?> request(String agencyCode, String principal, String token) {
		return HttpRequest.POST("/discovery/patrons/resolve",
				Map.of("agencyCode", agencyCode, "principal", principal))
			.bearerAuth(token);
	}
}
