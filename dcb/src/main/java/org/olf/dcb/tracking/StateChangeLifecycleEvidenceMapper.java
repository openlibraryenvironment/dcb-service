package org.olf.dcb.tracking;

import java.time.Instant;
import org.olf.dcb.request.lifecycle.LifecycleOperation;
import org.olf.dcb.request.lifecycle.LifecycleRole;
import org.olf.dcb.request.lifecycle.evidence.LifecycleEvidence;
import org.olf.dcb.request.lifecycle.evidence.LifecycleEvidenceResource;
import org.olf.dcb.request.lifecycle.evidence.LifecycleEvidenceSource;
import org.olf.dcb.tracking.model.StateChange;
import org.zalando.problem.Problem;
import org.zalando.problem.ThrowableProblem;

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

	// Every resource type is listed explicitly in both mappings. An unrecognised type must fail
	// here rather than fall through to a default: mapping it to BORROWER/REQUEST would write a
	// status belonging to some other resource onto the borrowing request.
	private static LifecycleRole roleFor(String resourceType) {
		if (resourceType == null) {
			throw unknownResourceType(null);
		}

		return switch (resourceType) {
			case "SupplierRequest", "SupplierItem" -> LifecycleRole.SUPPLIER;
			case "PickupRequest", "PickupItem" -> LifecycleRole.PICKUP;
			case "PatronRequest", "BorrowerVirtualItem" -> LifecycleRole.BORROWER;
			default -> throw unknownResourceType(resourceType);
		};
	}

	private static LifecycleEvidenceResource resourceFor(String resourceType) {
		if (resourceType == null) {
			throw unknownResourceType(null);
		}

		return switch (resourceType) {
			case "SupplierItem", "BorrowerVirtualItem", "PickupItem" ->
				LifecycleEvidenceResource.ITEM;
			case "SupplierRequest", "PatronRequest", "PickupRequest" ->
				LifecycleEvidenceResource.REQUEST;
			default -> throw unknownResourceType(resourceType);
		};
	}

	private static ThrowableProblem unknownResourceType(String resourceType) {
		return Problem.builder()
			.withTitle("State change record for unknown resource type")
			.with("resourceType", String.valueOf(resourceType))
			.build();
	}

	private static LifecycleOperation operationFor(
		LifecycleEvidenceResource resource) {

		return resource == LifecycleEvidenceResource.ITEM
			? LifecycleOperation.TRACK_ITEM
			: LifecycleOperation.TRACK_REQUEST;
	}
}
