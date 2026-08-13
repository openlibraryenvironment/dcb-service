package org.olf.dcb.api;

import static java.util.UUID.randomUUID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.oneOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.olf.dcb.security.RoleNames.ADMINISTRATOR;
import static org.olf.dcb.security.RoleNames.CONSORTIUM_ADMIN;
import static org.olf.dcb.security.RoleNames.DISCOVERY_SERVICE;
import static org.olf.dcb.security.RoleNames.LIBRARY_READ_ONLY;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.olf.dcb.security.TestStaticTokenValidator;
import org.olf.dcb.test.DcbTest;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

/**
 * The authorisation boundary between a discovery service and the staff request API.
 *
 * This replaces PatronRequestListApiTests, which proved that the PATRON role was
 * self-scoping on ONE endpoint. That was true and beside the point: the role sat on a
 * controller whose class default was IS_AUTHENTICATED, so it also reached request
 * placement, expedited checkout and state-machine rollback. The role is gone; these
 * tests exist so it cannot come back by accident, in either direction.
 */
@Slf4j
@DcbTest
@TestInstance(PER_CLASS)
class DiscoveryApiSecurityTests {

	private static final String DISCOVERY_TOKEN = "discovery-service-token";
	private static final String ADMIN_TOKEN = "discovery-tests-admin-token";
	private static final String ROLELESS_TOKEN = "discovery-tests-roleless-token";
	private static final String READ_ONLY_TOKEN = "discovery-tests-read-only-token";
	private static final String CONSORTIUM_ADMIN_TOKEN = "discovery-tests-consortium-admin-token";

	private static final UUID SOME_REQUEST_ID = randomUUID();

	/**
	 * Every route on the staff request API that a discovery credential must not reach.
	 * If any of these ever answers 2xx, a discovery service — or anything else holding
	 * a realm credential — can place requests as arbitrary patrons and rewrite request
	 * state.
	 */
	private static final List<String> STAFF_ONLY_MUTATIONS = List.of(
		"/patrons/requests/place",
		"/patrons/requests/place/walkup",
		"/patrons/requests/place/expeditedCheckout",
		"/patrons/requests/" + SOME_REQUEST_ID + "/rollback",
		"/patrons/requests/" + SOME_REQUEST_ID + "/update",
		"/patrons/requests/" + SOME_REQUEST_ID + "/transition/cleanup");

	@Inject
	@Client("/")
	private HttpClient client;

	@BeforeAll
	void beforeAll() {
		TestStaticTokenValidator.add(DISCOVERY_TOKEN, "a-discovery-service", List.of(DISCOVERY_SERVICE));
		TestStaticTokenValidator.add(ADMIN_TOKEN, "an-admin", List.of(ADMINISTRATOR));
		TestStaticTokenValidator.add(ROLELESS_TOKEN, "nobody", List.of());
		TestStaticTokenValidator.add(READ_ONLY_TOKEN, "front-desk", List.of(LIBRARY_READ_ONLY));
		TestStaticTokenValidator.add(CONSORTIUM_ADMIN_TOKEN, "a-consortium-admin",
			List.of(CONSORTIUM_ADMIN));
	}

	/**
	 * LIBRARY_READ_ONLY means read-only CONFIGURATION, not read-only circulation.
	 * dcb-admin-for-libraries confines those users to /requesting/*, which is exactly
	 * where staff requesting, walk-ups and expedited checkout live, so front-desk staff
	 * hold this role and place requests all day. Tightening the controller default
	 * without these three explicit grants 403s them, which is a regression this pins.
	 *
	 * A 400/404 here is fine and expected: it means authorisation PASSED and the empty
	 * body then failed validation. What must never come back is 401 or 403.
	 */
	@Test
	void readOnlyLibraryStaffCanStillReachThePlacementRoutes() {
		for (final var uri : List.of(
				"/patrons/requests/place",
				"/patrons/requests/place/walkup",
				"/patrons/requests/place/expeditedCheckout")) {

			final var status = statusOfRejectedPost(uri, READ_ONLY_TOKEN);

			assertThat(uri + " must remain reachable for front-desk staff", status,
				is(not(oneOf(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN))));
		}
	}

	/** ...but they must not be able to rewrite request state. */
	@Test
	void readOnlyLibraryStaffCannotMutateRequestState() {
		for (final var uri : List.of(
				"/patrons/requests/" + SOME_REQUEST_ID + "/rollback",
				"/patrons/requests/" + SOME_REQUEST_ID + "/transition/cleanup")) {

			assertThat(uri, statusOfRejectedPost(uri, READ_ONLY_TOKEN),
				is(oneOf(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN)));
		}
	}

	/**
	 * POSTs an empty body and reports the status. Every route here rejects an empty body,
	 * so a non-auth failure (400/404) is the signal that authorisation PASSED -- which is
	 * exactly what the read-only tests below need to distinguish from a 401/403.
	 */
	private HttpStatus statusOfRejectedPost(String uri, String token) {
		final var exception = assertThrows(HttpClientResponseException.class,
			() -> client.toBlocking().exchange(
				HttpRequest.POST(uri, Map.of()).bearerAuth(token)),
			uri + " unexpectedly succeeded with an empty body");

		return exception.getStatus();
	}

	// ---- a discovery credential must not reach the staff surface ----

	@Test
	void aDiscoveryCredentialCannotReachTheStaffRequestApi() {
		for (final var uri : STAFF_ONLY_MUTATIONS) {
			assertThat(uri, statusOfRejectedPost(uri, DISCOVERY_TOKEN),
				is(oneOf(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN)));
		}
	}

	@Test
	void aDiscoveryCredentialCannotReadRequestsByBarcode() {
		// PII-bearing staff search. A discovery service gets its patron's own requests
		// through /discovery/requests and nothing else.
		final var exception = assertThrows(HttpClientResponseException.class,
			() -> client.toBlocking().retrieve(
				HttpRequest.GET("/patrons/requests/some-host-lms?barcode=1234")
					.bearerAuth(DISCOVERY_TOKEN)));

		assertThat(exception.getStatus(), is(oneOf(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN)));
	}

	@Test
	void aDiscoveryCredentialCannotReachTheStatsEndpoints() {
		final var exception = assertThrows(HttpClientResponseException.class,
			() -> client.toBlocking().retrieve(
				HttpRequest.GET("/patrons/requests/stats/top-requestors").bearerAuth(DISCOVERY_TOKEN)));

		assertThat(exception.getStatus(), is(oneOf(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN)));
	}

	@Test
	void aRolelessCredentialCannotReachTheStaffRequestApiEither() {
		// The point of default-deny: "authenticated" is not a permission.
		for (final var uri : STAFF_ONLY_MUTATIONS) {
			assertThat(uri, statusOfRejectedPost(uri, ROLELESS_TOKEN),
				is(oneOf(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN)));
		}
	}

	// ---- and staff credentials must not reach the discovery surface ----

	@Test
	void aStaffCredentialCannotReachTheDiscoveryPatronSurface() {
		// Separation both ways: /discovery/** is for DISCOVERY_SERVICE only, so an
		// admin token cannot be used to read a patron's list without an assertion.
		final var exception = assertThrows(HttpClientResponseException.class,
			() -> client.toBlocking().retrieve(
				HttpRequest.GET("/discovery/requests").bearerAuth(ADMIN_TOKEN)));

		assertThat(exception.getStatus(), is(oneOf(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN)));
	}

	// ---- the discovery surface fails closed without a verified patron assertion ----

	@Test
	void theDiscoveryListRequiresAPatronAssertion() {
		// The DISCOVERY_SERVICE role proves WHO is calling, never WHICH PATRON. Without
		// a verified assertion there is no patron to scope to, so this must not fall
		// back to "all requests" — it must refuse.
		final var exception = assertThrows(HttpClientResponseException.class,
			() -> client.toBlocking().retrieve(
				HttpRequest.GET("/discovery/requests").bearerAuth(DISCOVERY_TOKEN)));

		assertThat(exception.getStatus(), is(HttpStatus.UNAUTHORIZED));
	}

	@Test
	void discoveryCancellationRequiresAPatronAssertion() {
		final var exception = assertThrows(HttpClientResponseException.class,
			() -> client.toBlocking().exchange(HttpRequest.POST(
				"/discovery/requests/" + SOME_REQUEST_ID + "/cancel", Map.of())
					.bearerAuth(DISCOVERY_TOKEN)));

		assertThat(exception.getStatus(), is(HttpStatus.UNAUTHORIZED));
	}

	@Test
	void anUnsignedPatronAssertionIsNotAccepted() {
		// Belt for the obvious attempt: a caller cannot simply name a patron in the
		// header and be believed.
		final var exception = assertThrows(HttpClientResponseException.class,
			() -> client.toBlocking().retrieve(HttpRequest.GET("/discovery/requests")
				.bearerAuth(DISCOVERY_TOKEN)
				.header("X-OpenRS-Patron-Assertion", "home-lms:patron-1")));

		assertThat(exception.getStatus(), is(HttpStatus.UNAUTHORIZED));
	}

	// ---- the legacy EBSCO Locate surface must keep working ----

	/**
	 * EBSCO Locate is an existing production integration holding an ADMIN service
	 * credential. It is closed-source and outside this workspace, so the compatibility
	 * audit that cleared the admin UIs could not see it, and the barcode route was
	 * renamed underneath it. Both URIs are served; this pins the legacy one, because a
	 * "tidy up the duplicate path" commit would take a live integration down and nothing
	 * else in the build would notice.
	 */
	@Test
	void theLegacyLocateBarcodeRouteIsStillServed() {
		final var response = client.toBlocking().exchange(
			HttpRequest.GET("/patrons/requests/patrons/some-host-lms/requests?barcode=1234")
				.bearerAuth(ADMIN_TOKEN));

		assertThat(response.getStatus(), is(HttpStatus.OK));
	}

	@Test
	void theCurrentBarcodeRouteIsServedToo() {
		final var response = client.toBlocking().exchange(
			HttpRequest.GET("/patrons/requests/some-host-lms?barcode=1234").bearerAuth(ADMIN_TOKEN));

		assertThat(response.getStatus(), is(HttpStatus.OK));
	}

	/**
	 * The legacy claims-scoped browse endpoint, also retained for Locate. What matters
	 * is that ADMIN still gets past authorisation: an empty result for a credential
	 * carrying no patron claims is the pre-existing behaviour and is not a regression.
	 */
	@Test
	void theLegacyBrowseRouteStillAuthorisesAnAdminCredential() {
		final var status = statusOfGet("/patrons/requests", ADMIN_TOKEN);

		assertThat(status, is(not(oneOf(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN))));
	}

	/** Locate's ADMIN grant is the exception; it does not extend to anything else. */
	@Test
	void theLegacyRoutesAreNotOpenToADiscoveryCredential() {
		for (final var uri : List.of(
				"/patrons/requests/patrons/some-host-lms/requests?barcode=1234",
				"/patrons/requests")) {

			assertThat(uri, statusOfGet(uri, DISCOVERY_TOKEN),
				is(oneOf(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN)));
		}
	}

	/**
	 * The exception is for ONE ADMIN service credential, so the legacy surface admits
	 * ADMINISTRATOR and nothing else -- not even CONSORTIUM_ADMIN, which reaches the
	 * current barcode path and has no need of the old one. Pinned because the obvious
	 * "tidy" is to fold the alias back onto the current method and inherit its wider
	 * role set, which would quietly widen the exception.
	 */
	@Test
	void theLegacySurfaceIsAdminOnly() {
		for (final var uri : List.of(
				"/patrons/requests/patrons/some-host-lms/requests?barcode=1234",
				"/patrons/requests")) {

			assertThat(uri, statusOfGet(uri, CONSORTIUM_ADMIN_TOKEN),
				is(oneOf(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN)));
		}
	}

	/** ...while the current barcode path keeps the role set it has always had. */
	@Test
	void theCurrentBarcodeRouteIsStillOpenToConsortiumAdmins() {
		final var response = client.toBlocking().exchange(
			HttpRequest.GET("/patrons/requests/some-host-lms?barcode=1234")
				.bearerAuth(CONSORTIUM_ADMIN_TOKEN));

		assertThat(response.getStatus(), is(HttpStatus.OK));
	}

	/** Reports the status of a GET whether it succeeded or failed. */
	private HttpStatus statusOfGet(String uri, String token) {
		try {
			return client.toBlocking().exchange(HttpRequest.GET(uri).bearerAuth(token)).getStatus();
		} catch (HttpClientResponseException e) {
			return e.getStatus();
		}
	}

	// ---- the anonymous directory stays anonymous ----

	@Test
	void theLibraryDirectoryNeedsNoCredential() {
		final var response = client.toBlocking().exchange(HttpRequest.GET("/discovery/libraries"));

		assertThat(response.getStatus(), is(HttpStatus.OK));
	}
}
