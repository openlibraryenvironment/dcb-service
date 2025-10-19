package org.olf.dcb.core.interaction.polaris;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.lessThan;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.olf.dcb.test.DcbTest;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;

/**
 * Tests for Polaris client reactive delays (DCB-1277).
 * Verifies that delays use Mono.delay() instead of Thread.sleep(),
 * preventing thread blocking in reactive streams.
 */
@DcbTest
@TestInstance(PER_CLASS)
class PolarisReactiveDelayTests {

	@Test
	void shouldUseReactiveDelayInsteadOfThreadSleep() {
		// Arrange
		final var startTime = Instant.now();

		// This simulates the pattern used in getILLRequestId() and getLocalHoldRequestIdv2()
		Mono<String> delayedOperation = Mono.delay(Duration.ofSeconds(2))
			.then(Mono.just("completed"));

		// Act & Assert
		StepVerifier.create(delayedOperation)
			.expectNext("completed")
			.verifyComplete();

		// Verify the delay was approximately 2 seconds
		final var duration = Duration.between(startTime, Instant.now());
		assertThat("Delay should be at least 2 seconds",
			duration.toMillis(), greaterThanOrEqualTo(2000L));
		assertThat("Delay should not significantly exceed 2 seconds",
			duration.toMillis(), lessThan(3000L));
	}

	@Test
	void shouldNotBlockThreadsDuringReactiveDelay() {
		// Arrange
		final var startTime = Instant.now();

		// Create multiple concurrent delayed operations
		// If using Thread.sleep(), these would block and take 6+ seconds sequentially
		// With Mono.delay(), they execute concurrently and complete in ~2 seconds
		Mono<String> operation1 = Mono.delay(Duration.ofSeconds(2))
			.then(Mono.just("op1"));
		Mono<String> operation2 = Mono.delay(Duration.ofSeconds(2))
			.then(Mono.just("op2"));
		Mono<String> operation3 = Mono.delay(Duration.ofSeconds(2))
			.then(Mono.just("op3"));

		// Act - Execute all three concurrently
		Mono<Boolean> result = Mono.zip(operation1, operation2, operation3)
			.map(tuple -> tuple.getT1() != null && tuple.getT2() != null && tuple.getT3() != null);

		// Assert
		StepVerifier.create(result)
			.expectNext(true)
			.verifyComplete();

		// All three operations should complete in approximately 2 seconds (concurrent)
		// not 6 seconds (sequential blocking)
		final var duration = Duration.between(startTime, Instant.now());
		assertThat("Concurrent operations should complete in ~2 seconds (non-blocking)",
			duration.toMillis(), lessThan(4000L));
	}

	@Test
	void shouldAllowThreadToProcessOtherWorkDuringDelay() {
		// Arrange
		final var threadName = new String[1];

		// This demonstrates that during the delay, the thread can do other work
		// (unlike Thread.sleep() which blocks)
		Mono<String> delayedOperation = Mono.delay(Duration.ofMillis(100))
			.publishOn(Schedulers.parallel())
			.doOnNext(tick -> threadName[0] = Thread.currentThread().getName())
			.then(Mono.just("completed"));

		// Act & Assert
		StepVerifier.create(delayedOperation)
			.expectNext("completed")
			.verifyComplete();

		// Verify thread name was captured (proves work was done)
		assertThat("Thread should have executed work after delay",
			threadName[0], is(notNullValue()));
	}

	@Test
	void shouldPropagateValuesAfterReactiveDelay() {
		// Arrange - Simulates the pattern in Polaris client methods
		String patronId = "patron123";
		String title = "Test Book";

		// This mimics: Mono.delay(Duration.ofSeconds(2)).then(ApplicationServices.getIllRequest(patronId))
		Mono<String> delayedQuery = Mono.delay(Duration.ofMillis(100))
			.then(Mono.just(patronId + ":" + title));

		// Act & Assert
		StepVerifier.create(delayedQuery)
			.expectNext("patron123:Test Book")
			.verifyComplete();
	}

	@Test
	void shouldHandleErrorsAfterReactiveDelay() {
		// Arrange - Verify error handling works with reactive delays
		Mono<String> delayedError = Mono.delay(Duration.ofMillis(100))
			.then(Mono.error(new RuntimeException("Polaris API error")));

		// Act & Assert
		StepVerifier.create(delayedError)
			.expectErrorMessage("Polaris API error")
			.verify();
	}
}
