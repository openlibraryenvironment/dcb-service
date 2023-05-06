package org.olf.reshare.dcb.tracking.model;

import io.micronaut.serde.annotation.Serdeable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
@Serdeable
public class LenderTrackingEvent  extends TrackingRecord {
	private static final String LENDER_TRACKING_RECORD = "LENDER";

	private String hostLmsCode;

	// ID of the patronRequest in the DCB database (A Local ID)
	private UUID internalPatronRequestId;

	// Is this an Item or an Instance (Bib in Sierra) hold
	private String normalisedRecordType;

	// ID Of the Item OR Instance being loaned at the lending system (A remote ID)
	private String localRecordId;

	// ID of the HOLD placed at the lending system (A remote ID)
	private String localHoldId;

	// The status of the hold - normalised to an internal value
	private String normalisedHoldStatus;

	private String localHoldStatusCode;

	private String localHoldStatusName;

	private String localPatronReference;

	private String pickupLocationCode;

	private String pickupLocationName;

	@Override
	public String getTrackigRecordType() {
		return LENDER_TRACKING_RECORD;
	}
}
