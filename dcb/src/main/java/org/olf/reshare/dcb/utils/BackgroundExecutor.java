package org.olf.reshare.dcb.utils;

import java.time.Duration;

import jakarta.inject.Singleton;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Singleton
public class BackgroundExecutor {
	public void executeAsynchronously(Mono<Void> task, Duration delay) {
		// Try to ensure next transition happens asynchronously to triggering flow
		task.delaySubscription(delay, Schedulers.boundedElastic())
			// Use a subscription to force publisher to be evaluated
			.subscribe();
	}
}
