package org.olf.dcb.api;

import static java.util.UUID.randomUUID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.oneOf;
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
import org.olf.dcb.core.model.PatronRequest.Outcome;
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
class ConfirmItemReturnedApiTests {
	private static final String SUPPLIER_TOKEN = "confirm-returned-supplier-token";
	private static final String BORROWER_TOKEN = "confirm-returned-borrower-token";
	private static final String CONSORTIUM_TOKEN = "confirm-returned-consortium-token";

	private static final String BORROWING_LMS = "CONFIRM_BORROWING_LMS";
	private static final String SUPPLYING_LMS = "CONFIRM_SUPPLYING_LMS";
	private static final String BORROWING_AGENCY = "CONFIRM_BORROWING_AG";
	private static final String SUPPLYING_AGENCY = "CONFIRM_SUPPLYING_AG";

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

	@BeforeAll
	void beforeAll() {
		deleteAll();

		final var borrowingHostLms = hostLmsFixture.createDummyHostLms(BORROWING_LMS);
		final var borrowingAgency = agencyFixture.defineAgency(BORROWING_AGENCY, BORROWING_AGENCY, borrowingHostLms);
		agencyFixture.defineAgency(SUPPLYING_AGENCY, SUPPLYING_AGENCY, hostLmsFixture.createDummyHostLms(SUPPLYING_LMS));

		patron = patronFixture.savePatron("home");
		identity = patronFixture.saveIdentityAndReturn(patron, borrowingHostLms, "patron-1",
			true, "-", "home", borrowingAgency);

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
	void theSupplyingLibraryConfirmsAParkedRequestAndItIsReleasedAsCancelled() {
		final var request = waitingForItsItem(Status.AWAITING_RETURN_TO_SUPPLIER, "CANCELLED");

		assertThat(post(request, SUPPLIER_TOKEN), is(HttpStatus.OK));

		final var released = patronRequestsFixture.findById(request.getId());
		assertThat(released.getStatus(), is(oneOf(Status.CANCELLED, Status.FINALISED)));
		assertThat(released.getOutcome(), is(Outcome.CANCELLED));
		assertThat(auditDescriptionsOf(request), hasItem("Item confirmed back at the supplying library"));
	}

	@Test
	void consortiumStaffConfirmAReturnInTransitAndItCompletesAsSupplied() {
		final var request = waitingForItsItem(Status.RETURN_TRANSIT, "TRANSIT");

		assertThat(post(request, CONSORTIUM_TOKEN), is(HttpStatus.OK));

		final var completed = patronRequestsFixture.findById(request.getId());
		assertThat(completed.getStatus(), is(oneOf(Status.COMPLETED, Status.FINALISED)));
		assertThat(completed.getOutcome(), is(Outcome.SUPPLIED));
	}

	@Test
	void theBorrowingLibraryCannotConfirmTheSuppliersShelf() {
		final var request = waitingForItsItem(Status.AWAITING_RETURN_TO_SUPPLIER, "CANCELLED");

		assertThat(refusal(request, BORROWER_TOKEN), is(HttpStatus.FORBIDDEN));
		assertThat(patronRequestsFixture.findById(request.getId()).getStatus(),
			is(Status.AWAITING_RETURN_TO_SUPPLIER));
	}

	@Test
	void anItemStillWithThePatronCannotBeConfirmedBack() {
		final var request = waitingForItsItem(Status.LOANED, "LOANED");

		assertThat(refusal(request, CONSORTIUM_TOKEN), is(HttpStatus.CONFLICT));
		assertThat(patronRequestsFixture.findById(request.getId()).getStatus(), is(Status.LOANED));
	}

	private PatronRequest waitingForItsItem(Status status, String supplierItemStatus) {
		final var request = patronRequestsFixture.savePatronRequest(PatronRequest.builder()
			.id(randomUUID())
			.patron(patron)
			.requestingIdentity(identity)
			.patronHostlmsCode(BORROWING_LMS)
			.activeWorkflow(STANDARD_WORKFLOW)
			.status(status)
			.build());

		supplierRequestsFixture.saveSupplierRequest(SupplierRequest.builder()
			.id(randomUUID())
			.patronRequest(request)
			.hostLmsCode(SUPPLYING_LMS)
			.localAgency(SUPPLYING_AGENCY)
			.localItemId("item-" + request.getId())
			.localItemStatus(supplierItemStatus)
			.isActive(true)
			.build());

		return request;
	}

	private HttpStatus post(PatronRequest request, String token) {
		return client.toBlocking().exchange(HttpRequest.POST(uriFor(request), Map.of()).bearerAuth(token))
			.getStatus();
	}

	private HttpStatus refusal(PatronRequest request, String token) {
		return assertThrows(HttpClientResponseException.class,
			() -> client.toBlocking().exchange(HttpRequest.POST(uriFor(request), Map.of()).bearerAuth(token)))
			.getStatus();
	}

	private static String uriFor(PatronRequest request) {
		return "/patrons/requests/" + request.getId() + "/transition/confirm-returned";
	}

	private List<String> auditDescriptionsOf(PatronRequest request) {
		return patronRequestsFixture.findAuditEntries(request).stream()
			.map(PatronRequestAudit::getBriefDescription)
			.toList();
	}
}
