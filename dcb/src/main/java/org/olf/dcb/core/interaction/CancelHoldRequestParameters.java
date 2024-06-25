package org.olf.dcb.core.interaction;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class CancelHoldRequestParameters {
	String localRequestId;
	String localItemId;
	String localItemBarcode;
	String patronBarcode;
}
