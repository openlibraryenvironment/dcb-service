package org.olf.dcb.core.interaction;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class PlaceHoldRequestParameters {
	String localPatronId;
	String recordType;
	String recordNumber;
	String localBibId;
	String localItemId;
	String pickupLocation;
	String note;
	String patronRequestId;
}
