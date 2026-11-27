package org.olf.dcb.api;

import static java.util.UUID.randomUUID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.olf.dcb.security.RoleNames.CONSORTIUM_ADMIN;
import static org.olf.dcb.security.RoleNames.LIBRARY_ADMIN;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.olf.dcb.core.api.serde.WatchedRequestPage;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.security.AgencyClaims;
import org.olf.dcb.security.TestStaticTokenValidator;
import org.olf.dcb.test.AgencyFixture;
import org.olf.dcb.test.DcbTest;
import org.olf.dcb.test.HostLmsFixture;
import org.olf.dcb.test.PatronFixture;
import org.olf.dcb.test.PatronRequestsFixture;

import io.micronaut.context.annotation.Property;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import jakarta.inject.Inject;

/** Watching follows the read rule: a library watches only requests it is party to. */
@DcbTest
@Property(name = "dcb.preferences.max-watched", value = "2")
@TestInstance(PER_CLASS)
class AdminWatchedRequestApiTests {
	private static final String BORROWER = "watch-tests-borrowing-librarian";
	private static final String OTHER = "watch-tests-other-librarian";
	private static final String CONSORTIUM = "watch-tests-consortium";

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

	private UUID first;
	private UUID second;
	private UUID third;

	@BeforeAll
	void beforeAll() {
		deleteAll();
		final var hostLms = hostLmsFixture.createDummyHostLms("WATCH_LIB_A");
		final var agency = agencyFixture.defineAgency("WATCH_AG_A", "WATCH_AG_A", hostLms);
		agencyFixture.defineAgency("WATCH_AG_C", "WATCH_AG_C", hostLmsFixture.createDummyHostLms("WATCH_LIB_C"));
		final var patron = patronFixture.savePatron("home");
		final var identity = patronFixture.saveIdentityAndReturn(patron, hostLms, "patron-1", true, "-", "home", agency);

		first = request(patron, identity);
		second = request(patron, identity);
		third = request(patron, identity);

		TestStaticTokenValidator.add(BORROWER, "borrowing-librarian", List.of(LIBRARY_ADMIN),
			Map.of("sub", "watch-borrower-0001", AgencyClaims.CODE, List.of("WATCH_AG_A")));
		TestStaticTokenValidator.add(OTHER, "other-librarian", List.of(LIBRARY_ADMIN),
			Map.of("sub", "watch-other-0002", AgencyClaims.CODE, List.of("WATCH_AG_C")));
		TestStaticTokenValidator.add(CONSORTIUM, "consortium", List.of(CONSORTIUM_ADMIN), Map.of("sub", "watch-cons-0003"));
	}

	@AfterAll
	void afterAll() {
		List.of(BORROWER, OTHER, CONSORTIUM).forEach(token -> {
			forget(token);
			TestStaticTokenValidator.invalidateToken(token);
		});
		deleteAll();
	}

	@BeforeEach
	void beforeEach() {
		List.of(BORROWER, OTHER, CONSORTIUM).forEach(this::forget);
	}

	@Test
	void anEmptyWatchListStillSaysContentIsEmpty() {
		final var body = client.toBlocking().retrieve(HttpRequest.GET("/preferences/watched").bearerAuth(BORROWER), String.class);

		assertThat(body, containsString("\"content\":[]"));
	}

	@Test
	void theBorrowingLibraryWatchesARequestOnceHoweverOftenItAsks() {
		watch(first, BORROWER);
		watch(first, BORROWER);

		final var page = watched(BORROWER);
		assertThat(page.totalSize(), is(1L));
		assertThat(page.content().stream().map(WatchedRequestPage.Watched::patronRequestId).toList(), contains(first));
	}

	@Test
	void aLibraryNotPartyToTheRequestGetsTheSameRefusalAsForOneThatDoesNotExist() {
		assertThat(statusOf(HttpRequest.PUT("/preferences/watched/" + first, Map.of()), OTHER), is(HttpStatus.FORBIDDEN));
		assertThat(statusOf(HttpRequest.PUT("/preferences/watched/" + randomUUID(), Map.of()), OTHER), is(HttpStatus.FORBIDDEN));
	}

	@Test
	void consortiumStaffWatchAnyRequestAndAreToldWhenOneDoesNotExist() {
		watch(first, CONSORTIUM);

		assertThat(watched(CONSORTIUM).totalSize(), is(1L));
		assertThat(statusOf(HttpRequest.PUT("/preferences/watched/" + randomUUID(), Map.of()), CONSORTIUM),
			is(HttpStatus.NOT_FOUND));
	}

	@Test
	void theLimitIsAConflictButRewatchingAtTheLimitIsNot() {
		watch(first, BORROWER);
		watch(second, BORROWER);

		assertThat(statusOf(HttpRequest.PUT("/preferences/watched/" + third, Map.of()), BORROWER), is(HttpStatus.CONFLICT));
		watch(second, BORROWER);
	}

	@Test
	void openingAWatchedRequestMarksItSeenAndStoppingIsIdempotent() throws InterruptedException {
		watch(first, BORROWER);
		final var watchedAt = watched(BORROWER).content().getFirst().dateLastViewed();

		Thread.sleep(5);
		client.toBlocking().exchange(HttpRequest.PUT("/preferences/watched/" + first + "/viewed", Map.of()).bearerAuth(BORROWER));
		assertThat(watched(BORROWER).content().getFirst().dateLastViewed().compareTo(watchedAt), is(greaterThan(0)));

		client.toBlocking().exchange(HttpRequest.DELETE("/preferences/watched/" + first).bearerAuth(BORROWER));
		client.toBlocking().exchange(HttpRequest.DELETE("/preferences/watched/" + first).bearerAuth(BORROWER));
		assertThat(watched(BORROWER).totalSize(), is(0L));
	}

	private UUID request(org.olf.dcb.core.model.Patron patron, org.olf.dcb.core.model.PatronIdentity identity) {
		return patronRequestsFixture.savePatronRequest(PatronRequest.builder()
			.id(randomUUID())
			.patron(patron)
			.requestingIdentity(identity)
			.status(PatronRequest.Status.LOANED)
			.build()).getId();
	}

	private void watch(UUID id, String token) {
		client.toBlocking().exchange(HttpRequest.PUT("/preferences/watched/" + id, Map.of()).bearerAuth(token));
	}

	private WatchedRequestPage watched(String token) {
		return client.toBlocking().retrieve(HttpRequest.GET("/preferences/watched").bearerAuth(token), WatchedRequestPage.class);
	}

	private HttpStatus statusOf(MutableHttpRequest<?> request, String token) {
		return assertThrows(HttpClientResponseException.class,
			() -> client.toBlocking().exchange(request.bearerAuth(token), String.class)).getStatus();
	}

	private void forget(String token) {
		client.toBlocking().exchange(HttpRequest.DELETE("/preferences").bearerAuth(token));
	}

	private void deleteAll() {
		patronRequestsFixture.deleteAll();
		patronFixture.deleteAllPatrons();
		agencyFixture.deleteAll();
		hostLmsFixture.deleteAll();
	}
}
