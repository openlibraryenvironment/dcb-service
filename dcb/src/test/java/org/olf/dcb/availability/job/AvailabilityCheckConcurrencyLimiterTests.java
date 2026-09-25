package org.olf.dcb.availability.job;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class AvailabilityCheckConcurrencyLimiterTests {
	@Test
	void limitsRemoteWorkGloballyAndPerSource() {
		final var limiter = new AvailabilityCheckConcurrencyLimiter(3, 2, 1);
		final var active = new AtomicInteger();
		final var highestActive = new AtomicInteger();
		final var activeBySource = new ConcurrentHashMap<UUID, AtomicInteger>();
		final var highestBySource = new ConcurrentHashMap<UUID, AtomicInteger>();
		final var firstSource = UUID.randomUUID();
		final var secondSource = UUID.randomUUID();
		final var sources = List.of(firstSource, firstSource, firstSource, firstSource,
			secondSource, secondSource, secondSource, secondSource);

		StepVerifier.create(Flux.fromIterable(sources)
			.flatMap(source -> limiter.withRemoteLimit(source, () -> delayedWork(
				active, highestActive,
				activeBySource.computeIfAbsent(source, ignored -> new AtomicInteger()),
				highestBySource.computeIfAbsent(source, ignored -> new AtomicInteger())))))
			.expectNextCount(sources.size())
			.verifyComplete();

		assertThat(highestActive.get(), is(3));
		assertThat(highestBySource.get(firstSource).get(), is(2));
		assertThat(highestBySource.get(secondSource).get(), is(2));
	}

	@Test
	void limitsMappingAndWriteWork() {
		final var limiter = new AvailabilityCheckConcurrencyLimiter(3, 2, 2);
		final var active = new AtomicInteger();
		final var highestActive = new AtomicInteger();

		StepVerifier.create(Flux.range(0, 8)
			.flatMap(ignored -> limiter.withMappingWriteLimit(() -> delayedWork(
				active, highestActive, null, null))))
			.expectNextCount(8)
			.verifyComplete();

		assertThat(highestActive.get(), is(2));
	}

	@Test
	void skipsCancelledWaitersWhenReleasingAPermit() {
		final var limiter = new AvailabilityCheckConcurrencyLimiter(1, 1, 1);
		final var first = limiter.withMappingWriteLimit(() -> Mono.<Integer>never()).subscribe();
		final var cancelled = limiter.withMappingWriteLimit(() -> Mono.just(2)).subscribe();

		cancelled.dispose();

		StepVerifier.create(limiter.withMappingWriteLimit(() -> Mono.just(3)))
			.then(first::dispose)
			.expectNext(3)
			.verifyComplete();
	}

	private static Mono<Integer> delayedWork(AtomicInteger active, AtomicInteger highestActive,
		AtomicInteger activeForSource, AtomicInteger highestForSource) {

		return Mono.defer(() -> {
			updateMaximum(highestActive, active.incrementAndGet());
			if (activeForSource != null) {
				updateMaximum(highestForSource, activeForSource.incrementAndGet());
			}
			return Mono.delay(Duration.ofMillis(25))
				.thenReturn(1)
				.doOnTerminate(() -> {
					active.decrementAndGet();
					if (activeForSource != null) {
						activeForSource.decrementAndGet();
					}
				});
		});
	}

	private static void updateMaximum(AtomicInteger maximum, int observed) {
		maximum.accumulateAndGet(observed, Math::max);
	}
}
