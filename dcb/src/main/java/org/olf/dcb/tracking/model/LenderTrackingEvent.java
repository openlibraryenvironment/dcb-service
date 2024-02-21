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
public class LenderTrackingEvent extends TrackingRecord {

	public static final String LENDER_TRACKING_RECORD = "LENDER";

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
	public String getTrackingRecordType() {
		return LENDER_TRACKING_RECORD;
	}
}
