package org.olf.dcb.tracking;

import jakarta.inject.Singleton;
import java.util.Map;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.SupplierRequest;
import org.olf.dcb.request.fulfilment.RequestWorkflowContext;
import org.olf.dcb.request.lifecycle.evidence.LifecycleEvidenceProjector;
import org.olf.dcb.tracking.model.StateChange;
import org.olf.dcb.tracking.model.TrackingRecord;
import org.zalando.problem.Problem;
import reactor.core.publisher.Mono;

@Singleton
public class LifecycleEvidenceTrackingEventSink implements TrackingEventSink {
	private final LifecycleEvidenceProjector lifecycleEvidenceProjector;
	private final StateChangeLifecycleEvidenceMapper mapper;

	public LifecycleEvidenceTrackingEventSink(
		LifecycleEvidenceProjector lifecycleEvidenceProjector) {

		this.lifecycleEvidenceProjector = lifecycleEvidenceProjector;
		this.mapper = new StateChangeLifecycleEvidenceMapper();
	}

	@Override
	public Mono<Map<String, Object>> onTrackingEvent(
		TrackingRecord trackingRecord) {

		if (!StateChange.STATE_CHANGE_RECORD.equals(
			trackingRecord.getTrackingRecordType())) {

			return Mono.empty();
		}

		if (!(trackingRecord instanceof StateChange stateChange)) {
			return Mono.error(Problem.builder()
				.withTitle("State change record has unexpected type")
				.with("TrackingRecord", trackingRecord)
				.build());
		}

		return lifecycleEvidenceProjector
			.project(mapper.map(stateChange), seedFrom(stateChange))
			.thenReturn(Map.of("StateChange", stateChange));
	}

	/**
	 * Tracking hands us the entity it is holding. Pass it to the projector so the status is
	 * written onto that instance rather than onto a second copy read back from the database -
	 * the caller evaluates workflow transitions against this object and later saves it.
	 */
	private static RequestWorkflowContext seedFrom(StateChange stateChange) {
		final var resource = stateChange.getResource();

		if (resource instanceof PatronRequest patronRequest) {
			return new RequestWorkflowContext().setPatronRequest(patronRequest);
		}

		if (resource instanceof SupplierRequest supplierRequest) {
			return new RequestWorkflowContext().setSupplierRequest(supplierRequest);
		}

		return null;
	}
}
