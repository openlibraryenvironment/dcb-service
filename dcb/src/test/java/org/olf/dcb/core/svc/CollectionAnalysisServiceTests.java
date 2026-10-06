package org.olf.dcb.core.svc;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.olf.dcb.core.api.serde.CollectionOverlapStat;
import org.olf.dcb.core.model.CollectionSnapshotRow;
import org.olf.dcb.storage.BibRepository;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * How the catalogue-wide queries are shared between callers. None of it is visible in the
 * figures - without it the endpoints return the same numbers by starting a 20,000,000-row pass
 * per caller and per panel - so it is asserted here. The repository is mocked: the sharing is
 * under test, not the SQL (CollectionAnalysisQueriesTests covers that).
 */
class CollectionAnalysisServiceTests {

	private static final UUID LIB_A = UUID.randomUUID();

	private static final List<CollectionSnapshotRow> ROWS = List.of(
		new CollectionSnapshotRow("PROFILE", LIB_A, "LIB_A", null, null, 2L, 1L),
		new CollectionSnapshotRow("FORMAT", LIB_A, "LIB_A", "Book", null, 2L, null),
		new CollectionSnapshotRow("HOLDERS", null, null, null, 1L, 1L, null),
		new CollectionSnapshotRow("HOLDERS", null, null, null, 2L, 1L, null),
		new CollectionSnapshotRow("HOLDINGS", null, null, null, null, 3L, 2L));

	private BibRepository bibRepository;
	private AtomicInteger snapshotPasses;

	@BeforeEach
	void beforeEach() {
		bibRepository = mock(BibRepository.class);
		snapshotPasses = new AtomicInteger();

		when(bibRepository.setLocalStatementTimeout(anyString())).thenReturn(Mono.just("600000"));
	}

	private CollectionAnalysisService service() {
		return new CollectionAnalysisService(bibRepository, Duration.ofMinutes(15),
			Duration.ofMinutes(10));
	}

	private void snapshotTakes(Duration duration) {
		when(bibRepository.getCollectionSnapshot()).thenReturn(Flux.defer(() -> {
			snapshotPasses.incrementAndGet();
			return Flux.fromIterable(ROWS).delaySubscription(duration);
		}));
	}

	@Test
	void theFourConsortiumFiguresShareOnePass() {
		snapshotTakes(Duration.ofMillis(200));

		final var service = service();

		// A cold dashboard opens every panel at once.
		Flux.merge(service.totals(), service.profile(), service.clusterSizeDistribution(),
			service.formatProfile()).blockLast(Duration.ofSeconds(10));

		assertThat(snapshotPasses.get(), equalTo(1));
	}

	@Test
	void repeatedCallsInsideTheTtlAskTheDatabaseOnce() {
		snapshotTakes(Duration.ZERO);

		final var service = service();

		service.totals().block();
		service.profile().block();
		service.totals().block();

		assertThat(snapshotPasses.get(), equalTo(1));
	}

	@Test
	void aCallerThatLeavesDoesNotCancelThePass() {
		snapshotTakes(Duration.ofMillis(300));

		final var service = service();

		// A proxy timing out: the first caller goes away while the pass is still running.
		service.totals().subscribe().dispose();

		// The retry reads the answer the abandoned pass produced instead of starting another.
		assertThat(service.totals().block(Duration.ofSeconds(10)).holdings(), equalTo(3L));
		assertThat(snapshotPasses.get(), equalTo(1));
	}

	@Test
	void aFailedPassIsNotKept() {
		final var attempts = new AtomicInteger();

		when(bibRepository.getCollectionSnapshot()).thenReturn(Flux.defer(() ->
			attempts.incrementAndGet() == 1
				? Flux.error(new IllegalStateException("canceling statement due to statement timeout"))
				: Flux.fromIterable(ROWS)));

		final var service = service();

		assertThrows(IllegalStateException.class, () -> service.totals().block());

		assertThat(service.totals().block().distinctTitles(), equalTo(2L));
		assertThat(attempts.get(), equalTo(2));
	}

	@Test
	void theStatementTimeoutIsSetBeforeThePass() {
		snapshotTakes(Duration.ZERO);

		service().totals().block();

		verify(bibRepository).setLocalStatementTimeout("600000");
	}

	@Test
	void anOverlapSelectionIsSharedWhateverOrderItsCodesArriveIn() {
		final var overlapPasses = new AtomicInteger();
		final var row = new CollectionOverlapStat(LIB_A, "LIB_A", UUID.randomUUID(), "LIB_C", 1L);

		when(bibRepository.getCollectionOverlapForLibrary("LIB_A,LIB_B")).thenReturn(
			Flux.defer(() -> {
				overlapPasses.incrementAndGet();
				return Flux.just(row);
			}));

		final var service = service();

		assertThat(service.overlapFor("LIB_B,LIB_A").block(), contains(row));
		assertThat(service.overlapFor(" LIB_A , LIB_B ,LIB_A").block(), contains(row));

		assertThat(overlapPasses.get(), equalTo(1));
		verify(bibRepository, times(1)).getCollectionOverlapForLibrary("LIB_A,LIB_B");
	}
}
