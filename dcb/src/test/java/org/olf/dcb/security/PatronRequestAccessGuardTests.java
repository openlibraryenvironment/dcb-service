package org.olf.dcb.security;

import static java.util.UUID.randomUUID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.SupplierRequest;
import org.olf.dcb.test.AgencyFixture;
import org.olf.dcb.test.DcbTest;
import org.olf.dcb.test.HostLmsFixture;
import org.olf.dcb.test.PatronFixture;
import org.olf.dcb.test.PatronRequestsFixture;
import org.olf.dcb.test.SupplierRequestsFixture;

import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.security.authentication.Authentication;
import io.micronaut.security.authentication.ServerAuthentication;
import jakarta.inject.Inject;

@DcbTest
@TestInstance(PER_CLASS)
class PatronRequestAccessGuardTests {
	@Inject
	private PatronRequestAccessGuard guard;

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

	private UUID borrowedFromAgB;

	@BeforeEach
	void beforeEach() {
		deleteAll();

		final var borrowingHostLms = hostLmsFixture.createDummyHostLms("LIB_A");
		final var borrowingAgency = agencyFixture.defineAgency("AG_A", "AG_A", borrowingHostLms);
		agencyFixture.defineAgency("AG_B", "AG_B", hostLmsFixture.createDummyHostLms("LIB_B"));
		agencyFixture.defineAgency("AG_C", "AG_C", hostLmsFixture.createDummyHostLms("LIB_C"));

		final var patron = patronFixture.savePatron("home");
		final var identity = patronFixture.saveIdentityAndReturn(patron, borrowingHostLms, "patron-1",
			true, "-", "home", borrowingAgency);

		final var patronRequest = patronRequestsFixture.savePatronRequest(PatronRequest.builder()
			.id(randomUUID())
			.patron(patron)
			.requestingIdentity(identity)
			.status(PatronRequest.Status.ERROR)
			.build());

		supplierRequestsFixture.saveSupplierRequest(SupplierRequest.builder()
			.id(randomUUID())
			.patronRequest(patronRequest)
			.localItemId("item-1")
			.localBibId("bib-1")
			.hostLmsCode("LIB_B")
			.localAgency("AG_B")
			.isActive(true)
			.build());

		borrowedFromAgB = patronRequest.getId();
	}

	@AfterAll
	void deleteAll() {
		patronRequestsFixture.deleteAll();
		patronFixture.deleteAllPatrons();
		agencyFixture.deleteAll();
		hostLmsFixture.deleteAll();
	}

	@Test
	void consortiumAdminIsNeverChecked() {
		final var unknown = randomUUID();

		assertThat(singleValueFrom(guard.requireOwnership(unknown, caller(RoleNames.CONSORTIUM_ADMIN))),
			equalTo(unknown));
	}

	@Test
	void adminIsNeverChecked() {
		final var unknown = randomUUID();

		assertThat(singleValueFrom(guard.requireOwnership(unknown, caller(RoleNames.ADMINISTRATOR))),
			equalTo(unknown));
	}

	@Test
	void consortiumAdminWhoAlsoHoldsLibraryAdminIsNeverChecked() {
		final var authentication = new ServerAuthentication("both",
			List.of(RoleNames.CONSORTIUM_ADMIN, RoleNames.LIBRARY_ADMIN),
			Map.of(AgencyClaims.CODE, List.of("AG_C")));

		assertThat(singleValueFrom(guard.requireOwnership(borrowedFromAgB, authentication)),
			equalTo(borrowedFromAgB));
	}

	@Test
	void libraryAdminAtTheBorrowingLibraryIsAllowed() {
		assertThat(singleValueFrom(guard.requireOwnership(borrowedFromAgB, librarian("AG_A"))),
			equalTo(borrowedFromAgB));
	}

	@Test
	void libraryAdminAtTheSupplyingLibraryIsAllowed() {
		assertThat(singleValueFrom(guard.requireOwnership(borrowedFromAgB, librarian("AG_B"))),
			equalTo(borrowedFromAgB));
	}

	@Test
	void libraryAdminAtAnotherLibraryIsRefused() {
		expectForbidden(borrowedFromAgB, librarian("AG_C"));
	}

	@Test
	void libraryAdminWithNoAgencyClaimIsRefused() {
		expectForbidden(borrowedFromAgB,
			new ServerAuthentication("librarian", List.of(RoleNames.LIBRARY_ADMIN), Map.of()));
	}

	@Test
	void libraryAdminAskingForAnUnknownIdGetsForbiddenNotNotFound() {
		expectForbidden(randomUUID(), librarian("AG_A"));
	}

	private void expectForbidden(UUID patronRequestId, Authentication authentication) {
		final var refusal = assertThrows(HttpStatusException.class,
			() -> singleValueFrom(guard.requireOwnership(patronRequestId, authentication)));

		assertThat(refusal.getStatus().getCode(), equalTo(403));
	}

	private static Authentication librarian(String agencyCode) {
		return new ServerAuthentication("librarian", List.of(RoleNames.LIBRARY_ADMIN),
			Map.of(AgencyClaims.CODE, List.of(agencyCode)));
	}

	private static Authentication caller(String role) {
		return new ServerAuthentication("staff", List.of(role), Map.of());
	}
}
