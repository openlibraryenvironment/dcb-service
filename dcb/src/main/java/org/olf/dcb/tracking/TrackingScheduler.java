package org.olf.dcb.tracking;

import io.micronaut.scheduling.annotation.Scheduled;
import jakarta.inject.Singleton;
import services.k_int.micronaut.scheduling.processor.AppTask;

@Singleton
public class TrackingScheduler {
	private final TrackingService trackingService;

	TrackingScheduler(TrackingService trackingService) {
		this.trackingService = trackingService;
	}

	@AppTask
	@Scheduled(initialDelay = "2m", fixedDelay = "${dcb.tracking.interval:5m}")
	public void run() {
		trackingService.run();
	}
}
