package org.olf.reshare.dcb.utils;

import java.time.Duration;

import io.micronaut.context.annotation.Value;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Singleton
public class BackgroundExecutor {
	private final Duration delay;

	/**
	 *
	 * @param delay Duration of delay before task is started
	 * Uses ISO-8601 format, as described in https://docs.oracle.com/javase/8/docs/api/java/time/Duration.html#parse-java.lang.CharSequence-
	 */
	@Inject
	public BackgroundExecutor(
		@Value("${dcb.background-execution.delay:PT0.0S}") String delay) {

		this.delay = Duration.parse(delay);
	}

	BackgroundExecutor(Duration delay) {
		this.delay = delay;
	}

	public void executeAsynchronously(Mono<Void> task) {
		// Try to ensure next transition happens asynchronously to triggering flow
		task.delaySubscription(delay, Schedulers.boundedElastic())
			// Use a subscription to force publisher to be evaluated
			.subscribe();
	}
}
