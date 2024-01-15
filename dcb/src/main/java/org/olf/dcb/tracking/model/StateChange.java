package org.olf.dcb.tracking.model;

import java.util.UUID;

import io.micronaut.serde.annotation.Serdeable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@EqualsAndHashCode(callSuper=false)
@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
@Serdeable
@ToString
public class StateChange  extends TrackingRecord {

	public static final String STATE_CHANGE_RECORD = "STATE_CHANGE";

	// We note the patron request that this change lives under for logging
	private UUID patronRequestId;

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
