package org.olf.dcb.tracking;

import java.time.Instant;
import org.olf.dcb.request.lifecycle.LifecycleOperation;
import org.olf.dcb.request.lifecycle.LifecycleRole;
import org.olf.dcb.request.lifecycle.evidence.LifecycleEvidence;
import org.olf.dcb.request.lifecycle.evidence.LifecycleEvidenceResource;
import org.olf.dcb.request.lifecycle.evidence.LifecycleEvidenceSource;
import org.olf.dcb.tracking.model.StateChange;

public class StateChangeLifecycleEvidenceMapper {
	public LifecycleEvidence map(StateChange stateChange) {
		final var role = roleFor(stateChange.getResourceType());
		final var resource = resourceFor(stateChange.getResourceType());

		return new LifecycleEvidence(
			LifecycleEvidenceSource.POLLING,
			null,
			role,
			operationFor(resource),
			resource,
			null,
			null,
			stateChange.getPatronRequestId() + ":" + role.name(),
			stateChange.getToState(),
			null,
			null,
			null,
			Instant.now(),
			null,
			stateChange.getResourceType(),
			stateChange.getResourceId(),
			stateChange.getFromState(),
			stateChange.getFromRenewalCount(),
			stateChange.getToRenewalCount(),
			stateChange.getFromHoldCount(),
			stateChange.getToHoldCount(),
			stateChange.getRenewable(),
			stateChange.getAdditionalProperties());
	}

	private static LifecycleRole roleFor(String resourceType) {
		return switch (resourceType) {
			case "SupplierRequest", "SupplierItem" -> LifecycleRole.SUPPLIER;
			case "PickupRequest", "PickupItem" -> LifecycleRole.PICKUP;
			default -> LifecycleRole.BORROWER;
		};
	}

	private static LifecycleEvidenceResource resourceFor(String resourceType) {
		return switch (resourceType) {
			case "SupplierItem", "BorrowerVirtualItem", "PickupItem" ->
				LifecycleEvidenceResource.ITEM;
			default -> LifecycleEvidenceResource.REQUEST;
		};
	}

	private static LifecycleOperation operationFor(
		LifecycleEvidenceResource resource) {

		return resource == LifecycleEvidenceResource.ITEM
			? LifecycleOperation.TRACK_ITEM
			: LifecycleOperation.TRACK_REQUEST;
	}
}
