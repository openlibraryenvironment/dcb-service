package org.olf.dcb.tracking;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.olf.dcb.request.lifecycle.LifecycleOperation;
import org.olf.dcb.request.lifecycle.LifecycleRole;
import org.olf.dcb.request.lifecycle.evidence.LifecycleEvidenceResource;
import org.olf.dcb.request.lifecycle.evidence.LifecycleEvidenceSource;
import org.olf.dcb.tracking.model.StateChange;

class StateChangeLifecycleEvidenceMapperTests {
	private final StateChangeLifecycleEvidenceMapper mapper =
		new StateChangeLifecycleEvidenceMapper();

	@Test
	void mapsSupplierRequestStateChangeToPollingRequestEvidence() {
		final var patronRequestId = UUID.randomUUID();
		final var supplierRequestId = UUID.randomUUID();

		final var evidence = mapper.map(StateChange.builder()
			.patronRequestId(patronRequestId)
			.resourceType("SupplierRequest")
			.resourceId(supplierRequestId.toString())
			.fromState("PLACED")
			.toState("CONFIRMED")
			.additionalProperties(Map.of("toRawStatus", "confirmed"))
			.build());

		assertThat(evidence.source(), is(LifecycleEvidenceSource.POLLING));
		assertThat(evidence.role(), is(LifecycleRole.SUPPLIER));
		assertThat(evidence.operation(), is(LifecycleOperation.TRACK_REQUEST));
		assertThat(evidence.resource(), is(LifecycleEvidenceResource.REQUEST));
		assertThat(evidence.correlationId(), is(patronRequestId + ":SUPPLIER"));
		assertThat(evidence.status(), is("CONFIRMED"));
		assertThat(evidence.fromStatus(), is("PLACED"));
		assertThat(evidence.trackingResourceType(), is("SupplierRequest"));
		assertThat(evidence.trackingResourceId(), is(supplierRequestId.toString()));
		assertThat(evidence.trackingProperties().get("toRawStatus"),
			is("confirmed"));
	}

	@Test
	void mapsBorrowerVirtualItemStateChangeToPollingItemEvidence() {
		final var patronRequestId = UUID.randomUUID();

		final var evidence = mapper.map(StateChange.builder()
			.patronRequestId(patronRequestId)
			.resourceType("BorrowerVirtualItem")
			.resourceId(patronRequestId.toString())
			.fromState("TRANSIT")
			.toState("LOANED")
			.fromRenewalCount(0)
			.toRenewalCount(1)
			.build());

		assertThat(evidence.role(), is(LifecycleRole.BORROWER));
		assertThat(evidence.operation(), is(LifecycleOperation.TRACK_ITEM));
		assertThat(evidence.resource(), is(LifecycleEvidenceResource.ITEM));
		assertThat(evidence.correlationId(), is(patronRequestId + ":BORROWER"));
		assertThat(evidence.fromRenewalCount(), is(0));
		assertThat(evidence.toRenewalCount(), is(1));
	}
}
