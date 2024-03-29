package org.olf.dcb.tracking.model;

import io.micronaut.serde.annotation.Serdeable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@EqualsAndHashCode(callSuper=false)
@Builder
@NoArgsConstructor
@Data
@AllArgsConstructor
@Serdeable
public class PickupTrackingEvent  implements TrackingRecord {
	private static final String PICKUP_TRACKING_RECORD = "PICKUP";

	private String hostLmsCode;

	@Override
	public String getTrackingRecordType() {
		return PICKUP_TRACKING_RECORD;
	}
}
