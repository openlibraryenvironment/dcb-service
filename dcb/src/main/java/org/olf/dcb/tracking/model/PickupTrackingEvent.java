package org.olf.dcb.tracking.model;

import io.micronaut.serde.annotation.Serdeable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@EqualsAndHashCode(callSuper=false)
@Builder
@NoArgsConstructor
@Data
@AllArgsConstructor
@Serdeable
public class PickupTrackingEvent  extends TrackingRecord {
	private static final String PICKUP_TRACKING_RECORD = "PICKUP";

	private String hostLmsCode;

	@Override
	public String getTrackigRecordType() {
		return PICKUP_TRACKING_RECORD;
	}
}
