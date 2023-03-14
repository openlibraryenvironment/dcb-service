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
