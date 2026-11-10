package org.olf.dcb.api;

import static java.util.UUID.randomUUID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.olf.dcb.core.model.WorkflowConstants.STANDARD_WORKFLOW;
import static org.olf.dcb.security.RoleNames.CONSORTIUM_ADMIN;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.olf.dcb.core.model.Patron;
import org.olf.dcb.core.model.PatronIdentity;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.PatronRequest.Status;
import org.olf.dcb.core.model.PatronRequestAudit;
import org.olf.dcb.core.model.SupplierRequest;
import org.olf.dcb.security.TestStaticTokenValidator;
import org.olf.dcb.storage.PatronRequestAuditRepository;
import org.olf.dcb.test.AgencyFixture;
import org.olf.dcb.test.DcbTest;
import org.olf.dcb.test.HostLmsFixture;
import org.olf.dcb.test.PatronFixture;
import org.olf.dcb.test.PatronRequestsFixture;
import org.olf.dcb.test.SupplierRequestsFixture;

import io.micronaut.context.annotation.Property;
import io.micronaut.data.r2dbc.operations.R2dbcOperations;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import jakarta.inject.Inject;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@DcbTest
@TestInstance(PER_CLASS)
@Property(name = "dcb.requests.lender-checkout-retry.per-caller-per-minute", value = "2")
class RetryLenderCheckoutLimitsApiTests {
	private static final String REQUEST_LIMIT_TOKEN = "retry-limits-request-token";
	private static final String CALLER_LIMIT_TOKEN = "retry-limits-caller-token";

	private static final String BORROWING_LMS = "RETRY_LIMITS_BORROWING_LMS";
	private static final String SUPPLYING_LMS = "RETRY_LIMITS_SUPPLYING_LMS";
	private static final String BORROWING_AGENCY = "RETRY_LIMITS_BORROWING_AG";
	private static final String SUPPLYING_AGENCY = "RETRY_LIMITS_SUPPLYING_AG";

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
	@Inject
	private SupplierRequestsFixture supplierRequestsFixture;
	@Inject
	private PatronRequestAuditRepository auditRepository;
	@Inject
	private R2dbcOperations r2dbcOperations;

	private Patron patron;
	private PatronIdentity identity;
	private PatronIdentity virtualIdentity;

	@BeforeAll
	void beforeAll() {
		deleteAll();

		final var borrowingHostLms = hostLmsFixture.createDummyHostLms(BORROWING_LMS);
		final var supplyingHostLms = hostLmsFixture.createDummyHostLms(SUPPLYING_LMS);
		final var borrowingAgency = agencyFixture.defineAgency(BORROWING_AGENCY, BORROWING_AGENCY, borrowingHostLms);
		final var supplyingAgency = agencyFixture.defineAgency(SUPPLYING_AGENCY, SUPPLYING_AGENCY, supplyingHostLms);

		patron = patronFixture.savePatron("home");
		identity = patronFixture.saveIdentityAndReturn(patron, borrowingHostLms, "patron-1",
			true, "-", "home", borrowingAgency);
		virtualIdentity = patronFixture.saveIdentityAndReturn(patron, supplyingHostLms, "virtual-1",
			false, "-", "home", supplyingAgency);

		TestStaticTokenValidator.add(REQUEST_LIMIT_TOKEN, "retry-limits-request-staff", List.of(CONSORTIUM_ADMIN), Map.of());
		TestStaticTokenValidator.add(CALLER_LIMIT_TOKEN, "retry-limits-caller-staff", List.of(CONSORTIUM_ADMIN), Map.of());
	}

	@AfterAll
	void afterAll() {
		TestStaticTokenValidator.invalidateToken(REQUEST_LIMIT_TOKEN);
		TestStaticTokenValidator.invalidateToken(CALLER_LIMIT_TOKEN);
		deleteAll();
	}

	private void deleteAll() {
		supplierRequestsFixture.deleteAll();
		patronRequestsFixture.deleteAll();
		patronFixture.deleteAllPatrons();
		agencyFixture.deleteAll();
		hostLmsFixture.deleteAll();
	}

	@Test
	void aRequestRetriedLessThanAMinuteAgoIsRefusedWithWhenToTryAgain() {
		final var request = onLoan();
		retryAuditedAt(request, Instant.now().minusSeconds(20));

		final var refusal = refusal(request, REQUEST_LIMIT_TOKEN);

		assertThat(refusal.getStatus(), is(HttpStatus.TOO_MANY_REQUESTS));
		assertThat(refusal.getResponse().getHeaders().get("Retry-After"), notNullValue());
	}

	@Test
	void aRequestLastRetriedOverAMinuteAgoIsTriedAgain() {
		final var request = onLoan();
		retryAuditedAt(request, Instant.now().minus(Duration.ofMinutes(2)));

		// The dummy lender returns no item, so the retry itself reaches the lender and is refused there
		assertThat(refusal(request, REQUEST_LIMIT_TOKEN).getStatus(), is(HttpStatus.BAD_GATEWAY));
	}

	@Test
	void aMemberOfStaffOverTheirRetriesForTheMinuteIsRefused() {
		assertThat(refusal(onLoan(), CALLER_LIMIT_TOKEN).getStatus(), is(HttpStatus.BAD_GATEWAY));
		assertThat(refusal(onLoan(), CALLER_LIMIT_TOKEN).getStatus(), is(HttpStatus.BAD_GATEWAY));

		final var third = refusal(onLoan(), CALLER_LIMIT_TOKEN);

		assertThat(third.getStatus(), is(HttpStatus.TOO_MANY_REQUESTS));
		assertThat(third.getResponse().getHeaders().get("Retry-After"), is("60"));
	}

	private PatronRequest onLoan() {
		final var request = patronRequestsFixture.savePatronRequest(PatronRequest.builder()
			.id(randomUUID())
			.patron(patron)
			.requestingIdentity(identity)
			.patronHostlmsCode(BORROWING_LMS)
			.activeWorkflow(STANDARD_WORKFLOW)
			.status(Status.LOANED)
			.needsAttention(true)
			.build());

		supplierRequestsFixture.saveSupplierRequest(SupplierRequest.builder()
			.id(randomUUID())
			.patronRequest(request)
			.hostLmsCode(SUPPLYING_LMS)
			.localAgency(SUPPLYING_AGENCY)
			.localId("hold-" + request.getId())
			.localItemId("item-" + request.getId())
			.localItemStatus("TRANSIT")
			.virtualIdentity(virtualIdentity)
			.isActive(true)
			.build());

		return request;
	}

	// audit_date is @DateCreated, so a save always stamps it now: back-date it afterwards
	private void retryAuditedAt(PatronRequest request, Instant when) {
		final var id = randomUUID();
		Mono.from(auditRepository.save(PatronRequestAudit.builder()
			.id(id)
			.patronRequest(request)
			.briefDescription("Lender checkout retry : Failed")
			.build())).block();

		Mono.from(r2dbcOperations.withConnection(connection -> Flux.from(connection
			.createStatement("UPDATE patron_request_audit SET audit_date = $1 WHERE id = $2")
			.bind("$1", when).bind("$2", id)
			.execute()).flatMap(result -> result.getRowsUpdated()))).block();
	}

	private HttpClientResponseException refusal(PatronRequest request, String token) {
		return assertThrows(HttpClientResponseException.class,
			() -> client.toBlocking().exchange(HttpRequest.POST(uriFor(request), Map.of()).bearerAuth(token)));
	}

	private static String uriFor(PatronRequest request) {
		return "/patrons/requests/" + request.getId() + "/transition/retry-lender-checkout";
	}
}
