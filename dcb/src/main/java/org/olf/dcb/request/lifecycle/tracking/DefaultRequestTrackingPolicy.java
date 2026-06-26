package org.olf.dcb.request.lifecycle.tracking;

import io.micronaut.context.annotation.Prototype;
import java.util.EnumSet;
import java.util.Set;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.request.fulfilment.RequestWorkflowContext;
import org.olf.dcb.request.lifecycle.LifecycleCapabilityResolver;
import org.olf.dcb.request.lifecycle.LifecycleRole;
import org.olf.dcb.request.lifecycle.TrackingMode;

@Prototype
public class DefaultRequestTrackingPolicy implements RequestTrackingPolicy {
	private final LifecycleCapabilityResolver capabilityResolver;

	public DefaultRequestTrackingPolicy(
		LifecycleCapabilityResolver capabilityResolver) {

		this.capabilityResolver = capabilityResolver;
	}

	@Override
	public TrackingMode modeFor(RequestWorkflowContext context) {
		final var trackedRoles = rolesTrackedAutomaticallyFor(
			context.getPatronRequest().getStatus());

		if (trackedRoles.isEmpty()) {
			return TrackingMode.SCHEDULED_POLL;
		}

		return trackedRoles.stream()
			.allMatch(role -> capabilityResolver.trackingMode(role) == TrackingMode.EVENT_DRIVEN)
				? TrackingMode.EVENT_DRIVEN
				: TrackingMode.SCHEDULED_POLL;
	}

	private static Set<LifecycleRole> rolesTrackedAutomaticallyFor(
		PatronRequest.Status status) {

		if (status == null) {
			return Set.of();
		}

		return switch (status) {
			case REQUEST_PLACED_AT_SUPPLYING_AGENCY ->
				EnumSet.of(LifecycleRole.SUPPLIER);
			case REQUEST_PLACED_AT_BORROWING_AGENCY ->
				EnumSet.of(LifecycleRole.SUPPLIER, LifecycleRole.BORROWER);
			default -> Set.of();
		};
	}
}
