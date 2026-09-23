package org.olf.dcb.core.interaction.polaris;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class PolarisItemEnrichmentConcurrencyTests {
	@Test
	void limitsConcurrentItemEnrichment() {
		final var active = new AtomicInteger();
		final var highestActive = new AtomicInteger();

		StepVerifier.create(PolarisLmsClient.enrichItemsWithBoundedConcurrency(
			Flux.range(0, 12), ignored -> delayedWork(active, highestActive)))
			.expectNextCount(12)
			.verifyComplete();

		assertThat(highestActive.get(), is(4));
		assertThat(active.get(), is(0));
	}

	private static Mono<Integer> delayedWork(AtomicInteger active, AtomicInteger highestActive) {
		return Mono.defer(() -> {
			highestActive.accumulateAndGet(active.incrementAndGet(), Math::max);
			return Mono.delay(Duration.ofMillis(25))
				.map(ignored -> 1)
				.doOnTerminate(active::decrementAndGet);
		});
	}
}
