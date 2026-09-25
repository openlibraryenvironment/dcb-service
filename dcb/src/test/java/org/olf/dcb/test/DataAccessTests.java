package org.olf.dcb.test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class DataAccessTests {
	@Test
	void usesOneDeleteConnectionAlongsideTheReadStream() {
		final var activeDeletes = new AtomicInteger();
		final var highestActiveDeletes = new AtomicInteger();

		new DataAccess().deleteAll(Flux.range(0, 12), ignored -> Mono.defer(() -> {
			final var active = activeDeletes.incrementAndGet();
			highestActiveDeletes.accumulateAndGet(active, Math::max);

			return Mono.delay(Duration.ofMillis(25))
				.then()
				.doOnTerminate(activeDeletes::decrementAndGet);
		}));

		assertThat(highestActiveDeletes.get(), is(1));
		assertThat(activeDeletes.get(), is(0));
	}
}
