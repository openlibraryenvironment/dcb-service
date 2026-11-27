package org.olf.dcb.storage;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.olf.dcb.core.model.PatronRequest.Status.LOANED;
import static org.olf.dcb.core.model.WorkflowConstants.STANDARD_WORKFLOW;
import static org.olf.dcb.test.PublisherUtils.manyValuesFrom;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.olf.dcb.core.api.serde.NetFlowStat;
import org.olf.dcb.core.model.DataAgency;
import org.olf.dcb.core.model.DataHostLms;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.test.AgencyFixture;
import org.olf.dcb.test.DcbTest;
import org.olf.dcb.test.HostLmsFixture;
import org.olf.dcb.test.PatronFixture;
import org.olf.dcb.test.PatronRequestsFixture;

import jakarta.inject.Inject;

/**
 * Two libraries on one shared system, each borrowing from and lending to another system. With
 * agencies named, a library's figures are its own: its patrons' requests on the borrowing side,
 * the requests it supplied on the lending side. Without, the system's as before.
 */
@DcbTest
class InsightsPerAgencyQueriesTests {
	@Inject
	private PatronRequestRepository requests;
	@Inject
	private PatronRequestsFixture patronRequestsFixture;
	@Inject
	private PatronFixture patronFixture;
	@Inject
	private AgencyFixture agencyFixture;
	@Inject
	private HostLmsFixture hostLmsFixture;

	@BeforeEach
	void beforeEach() {
		clearAll();

		final var shared = hostLmsFixture.createDummyHostLms("SHARED");
		final var other = hostLmsFixture.createDummyHostLms("OTHER");
		final var pine = agencyFixture.defineAgency("PINE", "Pine", shared);
		final var oak = agencyFixture.defineAgency("OAK", "Oak", shared);
		agencyFixture.defineAgency("ELM", "Elm", other);

		borrowedBy(shared, pine, "pine-patron", 2);
		borrowedBy(shared, oak, "oak-patron", 1);
		suppliedBy("PINE", 1);
		suppliedBy("OAK", 3);
	}

	@AfterEach
	void afterEach() {
		clearAll();
	}

	private void clearAll() {
		patronRequestsFixture.deleteAll();
		patronFixture.deleteAllPatrons();
		agencyFixture.deleteAll();
		hostLmsFixture.deleteAll();
	}

	private void borrowedBy(DataHostLms system, DataAgency library, String patronId, int count) {
		final var patron = patronFixture.savePatron(library.getCode());
		final var identity = patronFixture.saveIdentityAndReturn(patron, system, patronId, true, "ADULT",
			library.getCode(), library);

		for (int index = 0; index < count; index++) {
			patronRequestsFixture.savePatronRequest(PatronRequest.builder()
				.id(UUID.randomUUID())
				.status(LOANED)
				.activeWorkflow(STANDARD_WORKFLOW)
				.patronHostlmsCode(system.getCode())
				.requestingIdentity(identity)
				.localItemHostlmsCode("OTHER")
				.localItemAgencyCode("ELM")
				.build());
		}
	}

	private void suppliedBy(String library, int count) {
		for (int index = 0; index < count; index++) {
			patronRequestsFixture.savePatronRequest(PatronRequest.builder()
				.id(UUID.randomUUID())
				.status(LOANED)
				.activeWorkflow(STANDARD_WORKFLOW)
				.patronHostlmsCode("OTHER")
				.localItemHostlmsCode("SHARED")
				.localItemAgencyCode(library)
				.build());
		}
	}

	@Test
	void eachLibrarysBorrowingIsItsPatronsOwnAndTheSystemIsTheirSum() {
		assertThat(singleValueFrom(requests.countBorrowedForLibrary("SHARED", null, null, null)), equalTo(3L));
		assertThat(singleValueFrom(requests.countBorrowedForLibrary("SHARED", "PINE", null, null)), equalTo(2L));
		assertThat(singleValueFrom(requests.countBorrowedForLibrary("SHARED", "OAK", null, null)), equalTo(1L));
		assertThat(singleValueFrom(requests.countBorrowedForLibrary("SHARED", "PINE,OAK", null, null)), equalTo(3L));
	}

	@Test
	void eachLibrarysLendingIsWhatItSupplied() {
		assertThat(singleValueFrom(requests.countSuppliedForLibrary("SHARED", null, null, null)), equalTo(4L));
		assertThat(singleValueFrom(requests.countSuppliedForLibrary("SHARED", "PINE", null, null)), equalTo(1L));
		assertThat(singleValueFrom(requests.countSuppliedForLibrary("SHARED", "OAK", null, null)), equalTo(3L));
	}

	@Test
	void theDemandHeatmapCountsOneLibrarysPatrons() {
		final var cells = manyValuesFrom(requests.findDemandHeatmap("SHARED", "OAK", null, null));

		assertThat(cells.stream().mapToLong(cell -> cell.requestCount()).sum(), equalTo(1L));
	}

	@Test
	void netFlowNarrowsBothArmsToTheLibrary() {
		final var rows = manyValuesFrom(requests.findNetFlow("SHARED", "PINE", null, null));

		assertThat(rows.stream().map(NetFlowStat::libraryCode).toList(), containsInAnyOrder("SHARED"));
		assertThat(rows.get(0).borrowedCount(), equalTo(2L));
		assertThat(rows.get(0).suppliedCount(), equalTo(1L));
	}

	@Test
	void fillRateIsThePatronsLibrarysOwn() {
		final var oak = singleValueFrom(requests.getBorrowerFulfillmentStats("SHARED", "OAK", null, null));

		assertThat(oak.successfulCount() + oak.failedCount(), equalTo(1L));
	}
}
