package org.olf.dcb.api;

import static java.util.UUID.randomUUID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.olf.dcb.security.RoleNames.PATRON;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.security.TestStaticTokenValidator;
import org.olf.dcb.test.HostLmsFixture;
import org.olf.dcb.test.LocationFixture;
import org.olf.dcb.test.PatronFixture;
import org.olf.dcb.test.PatronRequestsFixture;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.olf.dcb.test.DcbTest;

/**
 * Proves the PATRON role addition to PatronRequestController.list() is
 * self-scoping: a patron JWT can only ever retrieve its own requests,
 * because identity comes exclusively from the token's claims.
 *
 * Also proves the discovery enrichment the endpoint owes its callers: the raw
 * DCB status survives alongside the patron-facing status, and the pickup
 * location code resolves to a name.
 */
@Slf4j
@DcbTest
@TestInstance(PER_CLASS)
class PatronRequestListApiTests {
	private static final String HOST_LMS_CODE = "prl-api-host-lms";

	private static final String PATRON_A_LOCAL_ID = "prl-patron-a";
	private static final String PATRON_B_LOCAL_ID = "prl-patron-b";

	private static final String PATRON_A_TOKEN = "prl-patron-a-token";
	private static final String CLAIMLESS_PATRON_TOKEN = "prl-claimless-patron-token";
	private static final String ROLELESS_TOKEN = "prl-roleless-token";

	private static final String PICKUP_LOCATION_CODE = "prl-pickup-code";
	private static final String PICKUP_LOCATION_NAME = "Patron Request List Test Library";

	@Inject
	@Client("/")
	private HttpClient client;

	@Inject
	private HostLmsFixture hostLmsFixture;
	@Inject
	private PatronFixture patronFixture;
	@Inject
	private PatronRequestsFixture patronRequestsFixture;
	@Inject
	private LocationFixture locationFixture;

	private UUID patronARequestId;
	private UUID patronBRequestId;

	@BeforeAll
	void beforeAll() {
		TestStaticTokenValidator.add(PATRON_A_TOKEN, "patron-a", List.of(PATRON),
			Map.of("localSystemCode", HOST_LMS_CODE,
				"localSystemPatronId", PATRON_A_LOCAL_ID));

		TestStaticTokenValidator.add(CLAIMLESS_PATRON_TOKEN, "claimless-patron",
			List.of(PATRON));

		TestStaticTokenValidator.add(ROLELESS_TOKEN, "roleless-user", List.of());

		patronFixture.deleteAllPatrons();
		hostLmsFixture.deleteAll();
		locationFixture.deleteAll();

		final var hostLms = hostLmsFixture.createDummyHostLms(HOST_LMS_CODE);

		locationFixture.createPickupLocation(PICKUP_LOCATION_NAME, PICKUP_LOCATION_CODE);

		final var patronA = patronFixture.definePatron(PATRON_A_LOCAL_ID, "home-lib-a", hostLms);
		final var patronB = patronFixture.definePatron(PATRON_B_LOCAL_ID, "home-lib-b", hostLms);

		patronARequestId = randomUUID();
		patronBRequestId = randomUUID();

		patronRequestsFixture.savePatronRequest(PatronRequest.builder()
			.id(patronARequestId)
			.patron(patronA)
			.status(PatronRequest.Status.READY_FOR_PICKUP)
			.activeWorkflow("RET-STD")
			.pickupLocationCode(PICKUP_LOCATION_CODE)
			.build());

		patronRequestsFixture.savePatronRequest(PatronRequest.builder()
			.id(patronBRequestId)
			.patron(patronB)
			.build());
	}

	@Test
	void patronTokenSeesOnlyItsOwnRequests() {
		final var body = client.toBlocking().retrieve(
			HttpRequest.GET("/patrons/requests").bearerAuth(PATRON_A_TOKEN));

		assertThat(body, containsString(patronARequestId.toString()));
		assertThat(body, not(containsString(patronBRequestId.toString())));
	}

	@Test
	void summaryCarriesBothRawAndPatronFacingStatus() {
		final var body = client.toBlocking().retrieve(
			HttpRequest.GET("/patrons/requests").bearerAuth(PATRON_A_TOKEN));

		// The raw state machine value is preserved for discovery services doing their own mapping
		assertThat(body, containsString("\"status\":\"READY_FOR_PICKUP\""));

		// ...alongside the coarse patron-facing status and its prose
		assertThat(body, containsString("\"discoveryStatus\":\"READY_FOR_PICKUP\""));
		assertThat(body, containsString("\"statusDescription\":\"Ready for pickup!\""));

		// What the discovery UIs split "my requests" from "my local requests" on
		assertThat(body, containsString("\"activeWorkflow\":\"RET-STD\""));
	}

	@Test
	void pickupLocationCodeIsResolvedToAName() {
		final var body = client.toBlocking().retrieve(
			HttpRequest.GET("/patrons/requests").bearerAuth(PATRON_A_TOKEN));

		assertThat(body, containsString("\"pickupLocationCode\":\"" + PICKUP_LOCATION_CODE + "\""));
		assertThat(body, containsString("\"pickupLocationName\":\"" + PICKUP_LOCATION_NAME + "\""));
	}

	@Test
	void patronTokenWithoutIdentityClaimsGetsNoRequests() {
		final var exception = assertThrows(HttpClientResponseException.class,
			() -> client.toBlocking().retrieve(
				HttpRequest.GET("/patrons/requests").bearerAuth(CLAIMLESS_PATRON_TOKEN)));

		// The controller returns an empty Mono when identity claims are absent,
		// which Micronaut surfaces as 404 — crucially, never another patron's data.
		assertThat(exception.getStatus(), is(HttpStatus.NOT_FOUND));
	}

	@Test
	void tokenWithoutPatronOrAdminRoleIsRejected() {
		final var exception = assertThrows(HttpClientResponseException.class,
			() -> client.toBlocking().retrieve(
				HttpRequest.GET("/patrons/requests").bearerAuth(ROLELESS_TOKEN)));

		// Micronaut's default rejection handler answers 401 here rather than
		// 403 — what matters is the request is denied with no data returned.
		assertThat(exception.getStatus(), is(HttpStatus.UNAUTHORIZED));
	}
}
