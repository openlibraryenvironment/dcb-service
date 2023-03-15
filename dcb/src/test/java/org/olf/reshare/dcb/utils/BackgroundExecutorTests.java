package org.olf.reshare.dcb.utils;

import static java.lang.Math.toIntExact;
import static java.lang.System.currentTimeMillis;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.lessThan;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import reactor.test.publisher.TestPublisher;

public class BackgroundExecutorTests {
	@Test
	void shouldScheduleTaskToRunAsynchronously() {
		final var backgroundExecutor = new BackgroundExecutor();

		final var testPublisher = TestPublisher.create();

		final var delayedMono = testPublisher.mono()
			.delayElement(Duration.ofMillis(1000)).then();

		final var startTime = currentTimeMillis();

		backgroundExecutor.executeAsynchronously(delayedMono);

		final var endTime = currentTimeMillis();

		final var duration = durationInMillis(startTime, endTime);

		// It should take much less time to return than trigger the transition
		assertThat(duration, is(lessThan(500)));

		// Check that the mono was subscribed to, otherwise it won't be executed
		testPublisher.assertWasSubscribed();
		testPublisher.assertWasRequested();
	}

	private static int durationInMillis(long startTime, long endTime) {
		return toIntExact(endTime - startTime);
	}
}
