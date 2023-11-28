package org.olf.dcb.core.interaction;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class PlaceHoldRequestParameters {
	String id;
	String recordType;
	String recordNumber;
	String pickupLocation;
	String note;
	String patronRequestId;
}
