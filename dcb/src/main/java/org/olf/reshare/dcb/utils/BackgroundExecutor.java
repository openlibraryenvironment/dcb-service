package org.olf.reshare.dcb.utils;

import java.time.Duration;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Singleton
public class BackgroundExecutor {
	private final Duration delay;

	@Inject
	public BackgroundExecutor() {
		this(Duration.ZERO);
	}

	/**
	 *
	 * @param delay Duration of delay before task is started
	 * Uses ISO-8601 format, as described in https://docs.oracle.com/javase/8/docs/api/java/time/Duration.html#parse-java.lang.CharSequence-
	 */
	public BackgroundExecutor(String delay) {
		this.delay = Duration.parse(delay);
	}

	public BackgroundExecutor(Duration delay) {
		this.delay = delay;
	}

	public void executeAsynchronously(Mono<Void> task) {
		// Try to ensure next transition happens asynchronously to triggering flow
		task.delaySubscription(delay, Schedulers.boundedElastic())
			// Use a subscription to force publisher to be evaluated
			.subscribe();
	}
}
