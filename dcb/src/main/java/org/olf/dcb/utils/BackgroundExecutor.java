package org.olf.dcb.utils;

import static java.time.Duration.ZERO;

import java.time.Duration;

import jakarta.inject.Singleton;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Singleton
public class BackgroundExecutor {
	public void executeAsynchronously(Mono<Void> task) {
		executeAsynchronously(task, ZERO);
	}

	public void executeAsynchronously(Mono<Void> task, Duration delay) {
		// Try to ensure next transition happens asynchronously to triggering flow
		task.delaySubscription(delay, Schedulers.boundedElastic())
			// Use a subscription to force publisher to be evaluated
			.subscribe();
	}
}
