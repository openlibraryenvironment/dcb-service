package org.olf.dcb.tracking;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

import reactor.core.publisher.Mono;

class TrackingSchedulerTests {
	@Test
	void delegatesToSelectedTrackingService() {
		final var trackingService = new RecordingTrackingService();
		final var scheduler = new TrackingScheduler(trackingService);

		scheduler.run();

		assertThat(trackingService.wasRun.get(), is(true));
	}

	private static class RecordingTrackingService implements TrackingService {
		private final AtomicBoolean wasRun = new AtomicBoolean(false);

		@Override
		public void run() {
			wasRun.set(true);
		}

		@Override
		public Mono<UUID> forceUpdate(UUID id) {
			return Mono.just(id);
		}
	}
}
