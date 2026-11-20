package org.olf.dcb.api;

import static java.util.UUID.randomUUID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.olf.dcb.core.model.WorkflowConstants.STANDARD_WORKFLOW;
import static org.olf.dcb.security.RoleNames.CONSORTIUM_ADMIN;
import static org.olf.dcb.security.RoleNames.LIBRARY_ADMIN;

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
import org.olf.dcb.security.AgencyClaims;
import org.olf.dcb.security.TestStaticTokenValidator;
import org.olf.dcb.test.AgencyFixture;
import org.olf.dcb.test.DcbTest;
import org.olf.dcb.test.HostLmsFixture;
import org.olf.dcb.test.PatronFixture;
import org.olf.dcb.test.PatronRequestsFixture;
import org.olf.dcb.test.SupplierRequestsFixture;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import jakarta.inject.Inject;

@DcbTest
@TestInstance(PER_CLASS)
class RetryLenderCheckoutApiTests {
	private static final String SUPPLIER_TOKEN = "retry-checkout-supplier-token";
	private static final String BORROWER_TOKEN = "retry-checkout-borrower-token";
	private static final String CONSORTIUM_TOKEN = "retry-checkout-consortium-token";

	private static final String BORROWING_LMS = "RETRY_BORROWING_LMS";
	private static final String SUPPLYING_LMS = "RETRY_SUPPLYING_LMS";
	private static final String BORROWING_AGENCY = "RETRY_BORROWING_AG";
	private static final String SUPPLYING_AGENCY = "RETRY_SUPPLYING_AG";

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

		TestStaticTokenValidator.add(SUPPLIER_TOKEN, "supplying-librarian", List.of(LIBRARY_ADMIN),
			Map.of(AgencyClaims.CODE, List.of(SUPPLYING_AGENCY)));
		TestStaticTokenValidator.add(BORROWER_TOKEN, "borrowing-librarian", List.of(LIBRARY_ADMIN),
			Map.of(AgencyClaims.CODE, List.of(BORROWING_AGENCY)));
		TestStaticTokenValidator.add(CONSORTIUM_TOKEN, "consortium-staff", List.of(CONSORTIUM_ADMIN), Map.of());
	}

	@AfterAll
	void afterAll() {
		TestStaticTokenValidator.invalidateToken(SUPPLIER_TOKEN);
		TestStaticTokenValidator.invalidateToken(BORROWER_TOKEN);
		TestStaticTokenValidator.invalidateToken(CONSORTIUM_TOKEN);
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
	void theBorrowingLibraryCannotRetryTheLendersCheckout() {
		final var request = onLoan(Status.LOANED);

		assertThat(refusal(request, BORROWER_TOKEN), is(HttpStatus.FORBIDDEN));
	}

	@Test
	void aRequestNotOnLoanHasNoLenderCheckoutToRetry() {
		final var request = onLoan(Status.RETURN_TRANSIT);

		assertThat(refusal(request, CONSORTIUM_TOKEN), is(HttpStatus.CONFLICT));
	}

	@Test
	void nothingIsRetriedWhenTheLendersCopyCannotBeRead() {
		// The dummy system returns no item, so DCB cannot check the copy is not already out
		final var request = onLoan(Status.LOANED);

		assertThat(refusal(request, SUPPLIER_TOKEN), is(HttpStatus.BAD_GATEWAY));
		assertThat(auditDescriptionsOf(request).stream()
			.noneMatch(description -> description.startsWith("Lender checkout retry")), is(true));
	}

	private PatronRequest onLoan(Status status) {
		final var request = patronRequestsFixture.savePatronRequest(PatronRequest.builder()
			.id(randomUUID())
			.patron(patron)
			.requestingIdentity(identity)
			.patronHostlmsCode(BORROWING_LMS)
			.activeWorkflow(STANDARD_WORKFLOW)
			.status(status)
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

	private HttpStatus refusal(PatronRequest request, String token) {
		return assertThrows(HttpClientResponseException.class,
			() -> client.toBlocking().exchange(HttpRequest.POST(uriFor(request), Map.of()).bearerAuth(token)))
			.getStatus();
	}

	private static String uriFor(PatronRequest request) {
		return "/patrons/requests/" + request.getId() + "/transition/retry-lender-checkout";
	}

	private List<String> auditDescriptionsOf(PatronRequest request) {
		return patronRequestsFixture.findAuditEntries(request).stream()
			.map(PatronRequestAudit::getBriefDescription)
			.toList();
	}
}
