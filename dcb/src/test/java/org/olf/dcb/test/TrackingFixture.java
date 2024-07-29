package org.olf.dcb.test;

import static org.olf.dcb.test.PublisherUtils.singleValueFrom;
import static org.olf.dcb.tracking.TrackingService.LOCK_NAME;

import java.util.UUID;

import org.olf.dcb.tracking.TrackingService;

import io.micronaut.context.annotation.Prototype;
import jakarta.inject.Singleton;
import services.k_int.federation.FederatedLockService;

@Singleton
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
		final var lockRelinquishedBefore = federatedLockService.waitMaxForNoFederatedLock(LOCK_NAME,
			10000);

		if (!lockRelinquishedBefore) {
			throw new RuntimeException("Lock prior to running tracking was not relinquished in time");
		}

		trackingService.run();

		// Wait for the lock to be released
		federatedLockService.waitMaxForNoFederatedLock(LOCK_NAME, 10000);
	}

	public void trackRequest(UUID id) {
		singleValueFrom(trackingService.forceUpdate(id).then());
	}
}
