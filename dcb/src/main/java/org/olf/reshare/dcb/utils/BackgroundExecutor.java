package org.olf.reshare.dcb.utils;

import jakarta.inject.Singleton;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Singleton
public class BackgroundExecutor {
	public void executeAsynchronously(Mono<Void> task) {
		// Try to ensure next transition happens asynchronously to triggering flow
		task.subscribeOn(Schedulers.boundedElastic())
			// Use a subscription to force publisher to be evaluated
			.subscribe();
	}
}
