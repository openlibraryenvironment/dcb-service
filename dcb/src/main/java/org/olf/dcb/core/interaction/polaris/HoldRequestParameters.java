package org.olf.dcb.core.interaction.polaris;

import lombok.Builder;
import lombok.Value;

@Builder
@Value
class HoldRequestParameters {
	String localPatronId;
	String recordNumber;
	String title;
	String pickupLocation;
	String dcbPatronRequestId;
	String note;
	Integer primaryMARCTOMID;
	Integer localItemLocationId;
	Integer bibliographicRecordID;
	String itemBarcode;
}
