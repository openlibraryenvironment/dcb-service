package org.olf.dcb.api;

import static java.util.UUID.randomUUID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.olf.dcb.security.RoleNames.LIBRARY_ADMIN;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.SupplierRequest;
import org.olf.dcb.security.AgencyClaims;
import org.olf.dcb.security.TestStaticTokenValidator;
import org.olf.dcb.test.AgencyFixture;
import org.olf.dcb.test.DcbTest;
import org.olf.dcb.test.HostLmsFixture;
import org.olf.dcb.test.PatronFixture;
import org.olf.dcb.test.PatronRequestsFixture;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import jakarta.inject.Inject;

@DcbTest
@TestInstance(PER_CLASS)
class PatronRequestLibraryScopeApiTests {
	private static final String OTHER_LIBRARY_TOKEN = "library-scope-tests-other-library-token";

	@Inject
	@Client("/")
	private HttpClient client;

	@Inject
	private AgencyFixture agencyFixture;
	@Inject
	private HostLmsFixture hostLmsFixture;
	@Inject
	private PatronFixture patronFixture;
	@Inject
	private PatronRequestsFixture patronRequestsFixture;

	private UUID patronRequestId;

	@BeforeAll
	void beforeAll() {
		deleteAll();

		final var borrowingHostLms = hostLmsFixture.createDummyHostLms("SCOPE_LIB_A");
		final var borrowingAgency = agencyFixture.defineAgency("SCOPE_AG_A", "SCOPE_AG_A", borrowingHostLms);
		agencyFixture.defineAgency("SCOPE_AG_C", "SCOPE_AG_C", hostLmsFixture.createDummyHostLms("SCOPE_LIB_C"));

		final var patron = patronFixture.savePatron("home");
		final var identity = patronFixture.saveIdentityAndReturn(patron, borrowingHostLms, "patron-1",
			true, "-", "home", borrowingAgency);

		final var patronRequest = patronRequestsFixture.savePatronRequest(PatronRequest.builder()
			.id(randomUUID())
			.patron(patron)
			.requestingIdentity(identity)
			.status(PatronRequest.Status.PICKUP_TRANSIT)
			.build());

		patronRequestId = patronRequest.getId();

		TestStaticTokenValidator.add(OTHER_LIBRARY_TOKEN, "other-librarian", List.of(LIBRARY_ADMIN),
			Map.of(AgencyClaims.CODE, List.of("SCOPE_AG_C")));
	}

	@AfterAll
	void afterAll() {
		TestStaticTokenValidator.invalidateToken(OTHER_LIBRARY_TOKEN);
		deleteAll();
	}

	private void deleteAll() {
		patronRequestsFixture.deleteAll();
		patronFixture.deleteAllPatrons();
		agencyFixture.deleteAll();
		hostLmsFixture.deleteAll();
	}

	@Test
	void anotherLibraryCannotForceCleanUpTheRequest() {
		assertThat(statusOf("/patrons/requests/" + patronRequestId + "/transition/cleanup?force=true"),
			is(HttpStatus.FORBIDDEN));

		assertThat(patronRequestsFixture.findById(patronRequestId).getStatus(),
			is(PatronRequest.Status.PICKUP_TRANSIT));
	}

	@Test
	void anotherLibraryCannotCheckTheRequestForUpdates() {
		assertThat(statusOf("/patrons/requests/" + patronRequestId + "/update"), is(HttpStatus.FORBIDDEN));
	}

	private HttpStatus statusOf(String uri) {
		return assertThrows(HttpClientResponseException.class,
			() -> client.toBlocking().exchange(HttpRequest.POST(uri, Map.of()).bearerAuth(OTHER_LIBRARY_TOKEN)))
			.getStatus();
	}
}
