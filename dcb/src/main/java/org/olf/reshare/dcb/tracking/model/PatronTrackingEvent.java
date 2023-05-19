package org.olf.reshare.dcb.tracking.model;

import io.micronaut.serde.annotation.Serdeable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder
@Data
@AllArgsConstructor
@Serdeable
@NoArgsConstructor

public class PatronTrackingEvent extends TrackingRecord {
	private static final String PATRON_TRACKING_RECORD = "PATRON";

	private String hostLmsCode;

	@Override
	public String getTrackigRecordType() {
		return PATRON_TRACKING_RECORD;
	}
}
