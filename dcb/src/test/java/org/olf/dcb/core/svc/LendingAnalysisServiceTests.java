package org.olf.dcb.core.svc;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.olf.dcb.core.model.PatronRequest.Status.LOANED;
import static org.olf.dcb.core.model.WorkflowConstants.STANDARD_WORKFLOW;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.olf.dcb.core.api.serde.LentTitleStat;
import org.olf.dcb.core.clustering.model.ClusterRecord;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.test.ClusterRecordFixture;
import org.olf.dcb.test.DcbTest;
import org.olf.dcb.test.PatronRequestsFixture;

import jakarta.inject.Inject;

/** What other libraries ask a library to lend (S-22): small groups folded, other libraries kept out. */
@DcbTest
class LendingAnalysisServiceTests {
	@Inject
	private LendingAnalysisService lendingAnalysis;
	@Inject
	private PatronRequestsFixture patronRequestsFixture;
	@Inject
	private ClusterRecordFixture clusterRecordFixture;

	@BeforeEach
	@AfterEach
	void clearAll() {
		patronRequestsFixture.deleteAll();
		clusterRecordFixture.deleteAll();
	}

	@Test
	void aTitleAskedForFiveTimesIsShownAndOneAskedForTwiceIsCountedAsOther() {
		final var popular = liveCluster("Popular");
		final var rare = liveCluster("Rare");
		final var elsewhere = liveCluster("Lent by someone else");

		supply("LEND_A", popular, 5);
		supply("LEND_A", rare, 2);
		supply("LEND_B", elsewhere, 6);

		final var analysis = lendingAnalysis.mostLent("LEND_A", null, null).block(Duration.ofMinutes(1));

		assertThat(analysis.groups().stream().map(LentTitleStat::clusterId).toList(), contains(popular));
		assertThat(analysis.groups().getFirst().requestCount(), is(5L));
		assertThat(analysis.other(), is(2L));
		assertThat(analysis.total(), is(7L));
	}

	@Test
	void formatsBelowTheMinimumAreFoldedToo() {
		supply("LEND_C", liveCluster("Only three"), 3);

		final var analysis = lendingAnalysis.byFormat("LEND_C", null, null).block(Duration.ofMinutes(1));

		assertThat(analysis.groups(), hasSize(0));
		assertThat(analysis.other(), is(3L));
	}

	@Test
	void theWindowIsAYearAtMostAndEndsNow() {
		final var now = Instant.parse("2026-10-28T12:00:00Z");
		final var window = LendingAnalysisService.Window.of(Instant.parse("2020-01-01T00:00:00Z"), null, now);

		assertThat(window.end(), is(now));
		assertThat(window.start(), is(now.minus(LendingAnalysisService.MAX_WINDOW)));
	}

	private UUID liveCluster(String title) {
		final var id = UUID.randomUUID();
		// Left unset, as clustering leaves is_deleted on a live cluster; the fixture's FALSE hides it
		clusterRecordFixture.createClusterRecord(ClusterRecord.builder()
			.id(id)
			.title(title)
			.bibs(Set.of())
			.dateCreated(Instant.now())
			.dateUpdated(Instant.now())
			.build());
		return id;
	}

	private void supply(String supplier, UUID clusterId, int times) {
		for (int index = 0; index < times; index++) {
			patronRequestsFixture.savePatronRequest(PatronRequest.builder()
				.id(UUID.randomUUID())
				.status(LOANED)
				.patronHostlmsCode("BORROWER")
				.localItemHostlmsCode(supplier)
				.bibClusterId(clusterId)
				.activeWorkflow(STANDARD_WORKFLOW)
				.build());
		}
	}
}
