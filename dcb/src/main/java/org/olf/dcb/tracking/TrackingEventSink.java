package org.olf.dcb.tracking;

import java.util.Map;
import org.olf.dcb.tracking.model.TrackingRecord;
import reactor.core.publisher.Mono;

public interface TrackingEventSink {
	Mono<Map<String, Object>> onTrackingEvent(TrackingRecord trackingRecord);
}
