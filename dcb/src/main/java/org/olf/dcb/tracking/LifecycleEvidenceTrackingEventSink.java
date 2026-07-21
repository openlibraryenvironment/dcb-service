package org.olf.dcb.tracking;

import jakarta.inject.Singleton;
import java.util.Map;
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

		return lifecycleEvidenceProjector.project(mapper.map(stateChange))
			.thenReturn(Map.of("StateChange", stateChange));
	}
}
