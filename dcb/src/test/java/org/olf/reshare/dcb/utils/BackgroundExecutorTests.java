package org.olf.reshare.dcb.utils;

import static java.lang.Math.toIntExact;
import static java.lang.System.currentTimeMillis;
import static java.time.Duration.ZERO;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.lessThan;

import java.time.Duration;

import org.awaitility.Awaitility;
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

		backgroundExecutor.executeAsynchronously(delayedMono, ZERO);

		final var endTime = currentTimeMillis();

		final var duration = durationInMillis(startTime, endTime);

		// It should take much less time to return than trigger the transition
		assertThat(duration, is(lessThan(500)));

		// Check that the mono was subscribed to, otherwise it won't be executed
		testPublisher.assertWasSubscribed();
		testPublisher.assertWasRequested();
	}

	@Test
	void canDelayTaskExecution() {
		// The values for the delay and the during and atMost parameters below
		// have been figured out by trial and error to try to get a reliable yet
		// low number to try to make the test run as quickly as possible.
		// Lower numbers were found to provide false failures.

		// Use the text based configuration for this
		final var backgroundExecutor = new BackgroundExecutor();

		final var testPublisher = TestPublisher.create();

		final var mono = testPublisher.mono().then();

		backgroundExecutor.executeAsynchronously(mono, Duration.ofMillis(600));

		Awaitility.await()
			// For at least this amount of time
			.during(Duration.ofMillis(200))
			// within at most this amount of time
			.atMost(Duration.ofMillis(400))
			// no subscription should have been made to the mono test publisher
			.until(() -> toIntExact(testPublisher.subscribeCount()), is(0));
	}

	private static int durationInMillis(long startTime, long endTime) {
		return toIntExact(endTime - startTime);
	}
}
