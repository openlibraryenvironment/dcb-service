package org.olf.dcb.test;

import static org.olf.dcb.test.PublisherUtils.singleValueFrom;

import java.util.UUID;

import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.tracking.TrackingService;

import jakarta.inject.Singleton;

@Singleton
public class TrackingFixture {
	private final TrackingService trackingService;

	public TrackingFixture(TrackingService trackingService) {
		this.trackingService = trackingService;
	}

	public void trackRequest(PatronRequest patronRequest) {
		trackRequest(patronRequest.getId());
	}

	public void trackRequest(UUID id) {
		// This is a compromise to track a single request,
		// without running the tracking service, as that starts a background task
		// that would be shared amongst tests
		singleValueFrom(trackingService.forceUpdate(id).then());
	}
}
