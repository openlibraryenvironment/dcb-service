package org.olf.dcb.core.svc;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.olf.dcb.core.api.serde.CollectionProfileStat;
import org.olf.dcb.core.api.serde.CollectionTotalsStat;
import org.olf.dcb.storage.BibRepository;

import io.micronaut.http.HttpStatus;
import io.micronaut.http.exceptions.HttpStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * The two controls that make the catalogue-wide queries safe to expose on demand.
 *
 * <p>Neither is visible functionally: without them these endpoints still return the right
 * numbers, just by starting several 20,000,000-row aggregates at once against the pool request
 * tracking shares. Nothing else in the suite would notice, which is why it is asserted here.
 *
 * <p>The repository is mocked - the gate is under test, not the SQL.
 */
class CollectionAnalysisServiceTests {

	private static final CollectionTotalsStat TOTALS = new CollectionTotalsStat(1L, 1L, 1L, 1L);

	private static final CollectionProfileStat PROFILE =
		new CollectionProfileStat(UUID.randomUUID(), "LIB_A", 1L, 1L);

	private BibRepository bibRepository;

	@BeforeEach
	void beforeEach() {
		bibRepository = mock(BibRepository.class);
	}

	private CollectionAnalysisService service() {
		// A wait budget long enough that nothing queues out during a normal test.
		return new CollectionAnalysisService(bibRepository, Duration.ofMinutes(15), 1,
			Duration.ofSeconds(10), Duration.ofMillis(10));
	}

	@Test
	void repeatedCallsInsideTheTtlAskTheDatabaseOnce() {
		when(bibRepository.getCollectionTotals()).thenReturn(Mono.just(TOTALS));

		final var service = service();

		assertThat(service.totals().block(), equalTo(TOTALS));
		assertThat(service.totals().block(), equalTo(TOTALS));
		assertThat(service.totals().block(), equalTo(TOTALS));

		// The point of the cache: a page refresh, or a second administrator, costs nothing.
		verify(bibRepository, times(1)).getCollectionTotals();
	}

	@Test
	void onlyOneCatalogueQueryRunsAtATime() {
		final var inFlight = new AtomicInteger();
		final var peak = new AtomicInteger();

		when(bibRepository.getCollectionTotals())
			.thenReturn(tracked(inFlight, peak, Mono.just(TOTALS)));

		when(bibRepository.getCollectionProfile())
			.thenReturn(tracked(inFlight, peak, Mono.just(PROFILE)));

		final var service = service();

		// Two DIFFERENT queries, subscribed together. The permit is one for the group, so the
		// second must wait - five distinct aggregates cost the same as five copies of one.
		Flux.merge(service.totals(), service.profile()).blockLast(Duration.ofSeconds(20));

		assertThat(peak.get(), equalTo(1));
	}

	@Test
	void aCallerThatQueuedTakesTheAnswerTheFirstOneCached() {
		when(bibRepository.getCollectionTotals())
			.thenReturn(Mono.delay(Duration.ofMillis(300)).thenReturn(TOTALS));

		final var service = service();

		// Both miss the cache, so both go for the permit. Whichever loses must re-check on the
		// way in - otherwise everybody who queued behind a cold computation runs it again.
		Flux.merge(service.totals(), service.totals()).blockLast(Duration.ofSeconds(20));

		verify(bibRepository, times(1)).getCollectionTotals();
	}

	@Test
	void refusesWithTooManyRequestsOnceTheWaitBudgetIsSpent() {
		when(bibRepository.getCollectionTotals())
			.thenReturn(Mono.delay(Duration.ofSeconds(2)).thenReturn(TOTALS));

		when(bibRepository.getCollectionProfile())
			.thenReturn(Flux.empty());

		// A 10ms budget in 10ms steps: the second caller gives up almost immediately.
		final var service = new CollectionAnalysisService(bibRepository, Duration.ofMinutes(15),
			1, Duration.ofMillis(10), Duration.ofMillis(10));

		final var held = service.totals().subscribe();

		try {
			final var refusal = assertThrows(HttpStatusException.class,
				() -> service.profile().block(Duration.ofSeconds(10)));

			// 429 rather than 503: the caller should retry, and by then the answer is cached.
			assertThat(refusal.getStatus(), equalTo(HttpStatus.TOO_MANY_REQUESTS));
		}
		finally {
			held.dispose();
		}
	}

	/** Counts how many subscriptions are live at once, and remembers the highest. */
	private static <T> Flux<T> tracked(AtomicInteger inFlight, AtomicInteger peak,
		Mono<T> work) {

		return Flux.defer(() -> {
			peak.accumulateAndGet(inFlight.incrementAndGet(), Math::max);

			return work.delayElement(Duration.ofMillis(200))
				.flux()
				.doFinally(signal -> inFlight.decrementAndGet());
		});
	}
}
