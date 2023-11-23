package org.olf.dcb.core.interaction;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class LocalRequest {
	String localId;
	String localStatus;
}
