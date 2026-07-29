package org.olf.dcb.request.lifecycle.tracking;

import io.micronaut.context.annotation.Prototype;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;
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
			context);

		if (trackedRoles.isEmpty()) {
			return TrackingMode.SCHEDULED_POLL;
		}

		final var trackingModes = trackedRoles.stream()
			.map(role -> trackingModeFor(context, role))
			.collect(Collectors.toSet());

		if (trackingModes.equals(Set.of(TrackingMode.EVENT_DRIVEN))) {
			return TrackingMode.EVENT_DRIVEN;
		}
		if (trackingModes.equals(Set.of(TrackingMode.SCHEDULED_POLL))) {
			return TrackingMode.SCHEDULED_POLL;
		}
		return TrackingMode.HYBRID;
	}

	@Override
	public boolean schedulesAutomaticPolls(
		RequestWorkflowContext context, LifecycleRole role) {

		final var trackedRoles = rolesTrackedAutomaticallyFor(context);

		// Unknown states retain the legacy polling behaviour.
		return trackedRoles.isEmpty()
			|| trackedRoles.contains(role)
				&& trackingModeFor(context, role) != TrackingMode.EVENT_DRIVEN;
	}

	private TrackingMode trackingModeFor(
		RequestWorkflowContext context, LifecycleRole role) {

		// Both borrower and supplier hosts are carried in-context, so tracking mode
		// resolves per-host. Pickup has no HostLms in context (only a client), so it
		// falls back to instance-wide config.
		return switch (role) {
			case BORROWER -> capabilityResolver.trackingMode(
				context.getPatronSystem(), LifecycleRole.BORROWER);
			case SUPPLIER -> capabilityResolver.trackingMode(
				context.getLenderSystem(), LifecycleRole.SUPPLIER);
			default -> capabilityResolver.trackingMode(role);
		};
	}

	private static Set<LifecycleRole> rolesTrackedAutomaticallyFor(
		RequestWorkflowContext context) {

		final var patronRequest = context.getPatronRequest();
		final var status = patronRequest.getStatus();

		if (status == null) {
			return Set.of();
		}

		return switch (status) {
			case REQUEST_PLACED_AT_SUPPLYING_AGENCY, CONFIRMED ->
				EnumSet.of(LifecycleRole.SUPPLIER);
			case REQUEST_PLACED_AT_BORROWING_AGENCY,
				REQUEST_PLACED_AT_PICKUP_AGENCY,
				PICKUP_TRANSIT,
				RECEIVED_AT_PICKUP,
				READY_FOR_PICKUP,
				LOANED,
				RETURN_TRANSIT -> rolesAfterBorrowerPlacement(patronRequest);
			default -> Set.of();
		};
	}

	private static Set<LifecycleRole> rolesAfterBorrowerPlacement(
		PatronRequest patronRequest) {

		final var roles = EnumSet.of(
			LifecycleRole.SUPPLIER, LifecycleRole.BORROWER);
		if (patronRequest.isUsingPickupAnywhereWorkflow()) {
			roles.add(LifecycleRole.PICKUP);
		}
		return roles;
	}
}
