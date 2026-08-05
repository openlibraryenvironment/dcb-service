package org.olf.dcb.request.lifecycle.tracking;

import org.olf.dcb.request.fulfilment.RequestWorkflowContext;
import org.olf.dcb.request.lifecycle.LifecycleRole;
import org.olf.dcb.request.lifecycle.TrackingMode;

public interface RequestTrackingPolicy {
	TrackingMode modeFor(RequestWorkflowContext context);

	boolean schedulesAutomaticPolls(
		RequestWorkflowContext context, LifecycleRole role);

	default boolean schedulesAutomaticPolls(RequestWorkflowContext context) {
		return modeFor(context) != TrackingMode.EVENT_DRIVEN;
	}
}
