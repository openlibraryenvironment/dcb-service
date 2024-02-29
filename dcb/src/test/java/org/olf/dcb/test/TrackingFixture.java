package org.olf.dcb.test;

import static org.olf.dcb.tracking.TrackingService.LOCK_NAME;

import org.olf.dcb.tracking.TrackingService;

import io.micronaut.context.annotation.Prototype;
import services.k_int.federation.FederatedLockService;

@Prototype
public class TrackingFixture {
	private final TrackingService trackingService;
	private final FederatedLockService federatedLockService;

	public TrackingFixture(TrackingService trackingService,
		FederatedLockService federatedLockService) {

		this.trackingService = trackingService;
		this.federatedLockService = federatedLockService;
	}

	public void runTracking() {
		// Because there is locking on runs of the tracking service we need to wait until we know it'll run.
		federatedLockService.waitMaxForNoFederatedLock(LOCK_NAME, 10000);

		trackingService.run();
	}
}
