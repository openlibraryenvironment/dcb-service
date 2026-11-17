package org.olf.dcb.api;

import static java.util.UUID.randomUUID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.olf.dcb.core.model.PatronRequest.Status.SUBMITTED_TO_DCB;
import static org.olf.dcb.security.RoleNames.DISCOVERY_SERVICE;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.PatronRequestAudit;
import org.olf.dcb.request.fulfilment.PatronRequestService;
import org.olf.dcb.security.TestStaticTokenValidator;
import org.olf.dcb.security.discovery.PatronAssertion;
import org.olf.dcb.security.discovery.PatronAssertionVerifier;
import org.olf.dcb.test.DcbTest;
import org.olf.dcb.test.HostLmsFixture;
import org.olf.dcb.test.PatronFixture;
import org.olf.dcb.test.PatronRequestsFixture;

import io.micronaut.context.annotation.Property;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.test.annotation.MockBean;
import jakarta.inject.Inject;
import reactor.core.publisher.Mono;

/**
 * A request or a loan created by a discovery service must say in the audit trail which
 * service vouched for the patron, as a cancellation already does.
 *
 * Placement itself is mocked: what is under test is the record the controller writes once
 * a request exists, not how the request came to exist.
 */
@DcbTest
@TestInstance(PER_CLASS)
// The discovery routes exist only with discovery on
@Property(name = "dcb.discovery.enabled", value = "true")
class DiscoveryPlacementAuditApiTests {

	private static final String ACCESS_TOKEN = "discovery-placement-audit-token";
	private static final String ASSERTING_SERVICE = "an-auditing-discovery-service";

	@Inject
	@Client("/")
	private HttpClient client;

	@Inject
	private PatronRequestsFixture patronRequestsFixture;
	@Inject
	private PatronFixture patronFixture;
	@Inject
	private HostLmsFixture hostLmsFixture;

	private PatronRequest placed;

	@MockBean(PatronAssertionVerifier.class)
	PatronAssertionVerifier assertionVerifier() {
		final var verifier = mock(PatronAssertionVerifier.class);

		doAnswer(invocation -> new PatronAssertion("audit-lms", "audit-patron", ASSERTING_SERVICE))
			.when(verifier).verify(any());

		return verifier;
	}

	@MockBean(PatronRequestService.class)
	PatronRequestService patronRequestService() {
		final var service = mock(PatronRequestService.class);

		doAnswer(invocation -> Mono.just(placed)).when(service).placePatronRequest(any());
		doAnswer(invocation -> Mono.just(placed)).when(service).placeDiscoveryWalkUp(any());

		return service;
	}

	@BeforeAll
	void beforeAll() {
		TestStaticTokenValidator.add(ACCESS_TOKEN, ASSERTING_SERVICE, List.of(DISCOVERY_SERVICE));

		hostLmsFixture.deleteAll();
		hostLmsFixture.createSierraHostLms("audit-lms");
	}

	@BeforeEach
	void beforeEach() {
		patronRequestsFixture.deleteAll();
		patronFixture.deleteAllPatrons();

		final var patron = patronFixture.definePatron("audit-patron", "home",
			hostLmsFixture.findByCode("audit-lms"));

		placed = patronRequestsFixture.savePatronRequest(PatronRequest.builder()
			.id(randomUUID())
			.patron(patron)
			.status(SUBMITTED_TO_DCB)
			.patronHostlmsCode("audit-lms")
			.build());
	}

	@Test
	void aPlacedRequestRecordsTheAssertingService() {
		final var response = client.toBlocking().exchange(
			HttpRequest.POST("/discovery/requests/place", Map.of(
					"bibClusterId", randomUUID().toString(),
					"pickupLocationCode", randomUUID().toString()))
				.bearerAuth(ACCESS_TOKEN));

		assertThat(response.getStatus(), is(HttpStatus.OK));
		assertThat(assertingServicesRecordedFor(placed), hasItem(ASSERTING_SERVICE));
	}

	@Test
	void aWalkUpCheckoutRecordsTheAssertingService() {
		final var response = client.toBlocking().exchange(
			HttpRequest.POST("/discovery/requests/place/expedited", Map.of(
					"bibClusterId", randomUUID().toString(),
					"pickupLocationCode", randomUUID().toString(),
					"itemLocalId", "item-1",
					"itemHostLmsCode", "audit-lms",
					"itemAgencyCode", "audit-agency"))
				.bearerAuth(ACCESS_TOKEN));

		assertThat(response.getStatus(), is(HttpStatus.OK));
		assertThat(assertingServicesRecordedFor(placed), hasItem(ASSERTING_SERVICE));
	}

	private List<Object> assertingServicesRecordedFor(PatronRequest request) {
		return patronRequestsFixture.findAuditEntries(request).stream()
			.map(PatronRequestAudit::getAuditData)
			.filter(data -> data != null)
			.map(data -> data.get("assertedByDiscoveryService"))
			.toList();
	}
}
