package org.olf.dcb.tracking.model;

import io.micronaut.serde.annotation.Serdeable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@EqualsAndHashCode(callSuper=false)
@Builder
@Data
@AllArgsConstructor
@Serdeable
@NoArgsConstructor
public class PatronTrackingEvent implements TrackingRecord {
	private static final String PATRON_TRACKING_RECORD = "PATRON";

	private String hostLmsCode;

	@Override
	public String getTrackingRecordType() {
		return PATRON_TRACKING_RECORD;
	}
}
