package org.olf.dcb.tracking.model;

import java.util.UUID;

import io.micronaut.serde.annotation.Serdeable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
@Serdeable
@ToString
public class StateChange  extends TrackingRecord {

	public static final String STATE_CHANGE_RECORD = "STATE_CHANGE";

        private String resourceType;
        private String resourceId;
        private String fromState;
        private String toState;

	@ToString.Exclude
        private Object resource;

	@Override
	public String getTrackigRecordType() {
		return STATE_CHANGE_RECORD;
	}
}
