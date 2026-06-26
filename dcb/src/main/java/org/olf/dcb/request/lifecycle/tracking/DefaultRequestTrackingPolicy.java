package org.olf.dcb.request.lifecycle.tracking;

import io.micronaut.context.annotation.Prototype;
import org.olf.dcb.request.fulfilment.RequestWorkflowContext;
import org.olf.dcb.request.lifecycle.TrackingMode;

@Prototype
public class DefaultRequestTrackingPolicy implements RequestTrackingPolicy {
	@Override
	public TrackingMode modeFor(RequestWorkflowContext context) {
		return TrackingMode.SCHEDULED_POLL;
	}
}
