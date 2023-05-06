package org.olf.reshare.dcb.tracking.model;

import io.micronaut.serde.annotation.Serdeable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

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
