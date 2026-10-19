package org.olf.dcb.security;

import static java.util.UUID.randomUUID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.olf.dcb.test.PublisherUtils.manyValuesFrom;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.olf.dcb.core.model.InactiveSupplierRequest;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.PatronRequestAudit;
import org.olf.dcb.core.model.SupplierRequest;
import org.olf.dcb.storage.PatronRequestAuditRepository;
import org.olf.dcb.storage.PatronRequestRepository;
import org.olf.dcb.storage.postgres.PostgresPatronRequestAuditRepository;
import org.olf.dcb.storage.postgres.PostgresPatronRequestRepository;
import org.olf.dcb.test.AgencyFixture;
import org.olf.dcb.test.DcbTest;
import org.olf.dcb.test.HostLmsFixture;
import org.olf.dcb.test.InactiveSupplierRequestsFixture;
import org.olf.dcb.test.LocationFixture;
import org.olf.dcb.test.PatronFixture;
import org.olf.dcb.test.PatronRequestsFixture;
import org.olf.dcb.test.SupplierRequestsFixture;

import jakarta.inject.Inject;

@DcbTest
@TestInstance(PER_CLASS)
class RequestInvolvementTests {
	private static final List<String> EVERY_AGENCY =
		List.of("INV_BORROWER", "INV_CURRENT", "INV_EARLIER", "INV_PICKUP", "INV_STRANGER");

	@Inject
	private PostgresPatronRequestRepository patronRequestRepository;
	@Inject
	private PostgresPatronRequestAuditRepository auditRepository;
	@Inject
	private PatronRequestRepository requestWriter;
	@Inject
	private PatronRequestAuditRepository auditWriter;

	@Inject
	private AgencyFixture agencyFixture;
	@Inject
	private HostLmsFixture hostLmsFixture;
	@Inject
	private LocationFixture locationFixture;
	@Inject
	private PatronFixture patronFixture;
	@Inject
	private PatronRequestsFixture patronRequestsFixture;
	@Inject
	private SupplierRequestsFixture supplierRequestsFixture;
	@Inject
	private InactiveSupplierRequestsFixture inactiveSupplierRequestsFixture;

	private UUID requestId;
	private UUID auditId;

	@BeforeEach
	void beforeEach() {
		deleteAll();

		final var hostLms = hostLmsFixture.createDummyHostLms("INV_LMS");
		final var borrower = agencyFixture.defineAgency("INV_BORROWER", "Borrower", hostLms);
		agencyFixture.defineAgency("INV_CURRENT", "Current supplier", hostLms);
		agencyFixture.defineAgency("INV_EARLIER", "Earlier supplier", hostLms);
		final var pickup = agencyFixture.defineAgency("INV_PICKUP", "Pickup", hostLms);
		agencyFixture.defineAgency("INV_STRANGER", "Stranger", hostLms);

		final var pickupLocation = locationFixture.createPickupLocation(pickup);

		final var patron = patronFixture.savePatron("home");
		final var identity = patronFixture.saveIdentityAndReturn(patron, hostLms, "patron-1",
			true, "-", "home", borrower);

		final var request = patronRequestsFixture.savePatronRequest(PatronRequest.builder()
			.id(randomUUID())
			.patron(patron)
			.requestingIdentity(identity)
			.pickupLocationCode(pickupLocation.getId().toString())
			.status(PatronRequest.Status.REQUEST_PLACED_AT_SUPPLYING_AGENCY)
			.build());

		supplierRequestsFixture.saveSupplierRequest(SupplierRequest.builder()
			.id(randomUUID())
			.patronRequest(request)
			.localItemId("item-current")
			.hostLmsCode("INV_LMS")
			.localAgency("INV_CURRENT")
			.isActive(true)
			.build());

		inactiveSupplierRequestsFixture.save(InactiveSupplierRequest.builder()
			.id(randomUUID())
			.patronRequest(request)
			.localItemId("item-earlier")
			.hostLmsCode("INV_LMS")
			.localAgency("INV_EARLIER")
			.build());

		auditId = singleValueFrom(auditWriter.save(PatronRequestAudit.builder()
			.id(randomUUID())
			.patronRequest(request)
			.auditDate(Instant.now())
			.briefDescription("placed")
			.build())).getId();

		requestId = request.getId();
	}

	@AfterAll
	void deleteAll() {
		patronRequestsFixture.deleteAll();
		patronFixture.deleteAllPatrons();
		locationFixture.deleteAll();
		agencyFixture.deleteAll();
		hostLmsFixture.deleteAll();
	}

	@Test
	void everyPartyToTheRequestSeesIt() {
		for (var party : List.of("INV_BORROWER", "INV_CURRENT", "INV_PICKUP")) {
			assertThat(party, requestIdsVisibleTo(party), is(List.of(requestId)));
		}
	}

	@Test
	void aLibraryThatIsNotPartyToTheRequestDoesNotSeeIt() {
		assertThat(requestIdsVisibleTo("INV_STRANGER"), is(empty()));
	}

	@Test
	void anEarlierSupplierWhoseAttemptDidNotGoAheadDoesNotSeeIt() {
		assertThat(requestIdsVisibleTo("INV_EARLIER"), is(empty()));
		assertThat(auditIdsVisibleTo("INV_EARLIER"), is(empty()));
	}

	@Test
	void noAgenciesSeeNothing() {
		assertThat(manyValuesFrom(patronRequestRepository.findAll(RequestInvolvement.involving(List.of()))),
			is(empty()));
	}

	@Test
	void theInvolvedAgenciesOfTheRequestAreEveryParty() {
		assertThat(manyValuesFrom(patronRequestRepository.findInvolvedAgencyCodes(requestId)),
			containsInAnyOrder("INV_BORROWER", "INV_CURRENT", "INV_PICKUP"));
	}

	@Test
	void theSqlAndTheSpecificationAgreeForEveryAgency() {
		final Set<String> involved = Set.copyOf(manyValuesFrom(patronRequestRepository.findInvolvedAgencyCodes(requestId)));

		for (var agency : EVERY_AGENCY) {
			assertThat(agency, !requestIdsVisibleTo(agency).isEmpty(), is(involved.contains(agency)));
		}
	}

	@Test
	void aPickupCodeThatIsNotAUuidInvolvesNoPickupLibraryAndFailsNothing() {
		final var request = patronRequestsFixture.findById(requestId);
		request.setPickupLocationCode("not-a-uuid");
		singleValueFrom(requestWriter.update(request));

		assertThat(manyValuesFrom(patronRequestRepository.findInvolvedAgencyCodes(requestId)),
			containsInAnyOrder("INV_BORROWER", "INV_CURRENT"));
		assertThat(requestIdsVisibleTo("INV_PICKUP"), is(empty()));
	}

	@Test
	void anAuditIsVisibleToEveryPartyToItsRequest() {
		assertThat(auditIdsVisibleTo("INV_PICKUP"), is(List.of(auditId)));
		assertThat(auditIdsVisibleTo("INV_STRANGER"), is(empty()));
	}

	@Test
	void aRequestWithSeveralMatchingPartiesIsReturnedOnce() {
		assertThat(manyValuesFrom(patronRequestRepository.findAll(
				RequestInvolvement.involving(List.of("INV_BORROWER", "INV_PICKUP", "INV_CURRENT")))),
			hasSize(1));
	}

	private List<UUID> requestIdsVisibleTo(String agencyCode) {
		return manyValuesFrom(patronRequestRepository.findAll(RequestInvolvement.involving(List.of(agencyCode))))
			.stream().map(PatronRequest::getId).toList();
	}

	private List<UUID> auditIdsVisibleTo(String agencyCode) {
		return manyValuesFrom(auditRepository.findAll(
				RequestInvolvement.<PatronRequestAudit>involvingRequestVia("patronRequest", List.of(agencyCode))))
			.stream().map(PatronRequestAudit::getId).toList();
	}
}
