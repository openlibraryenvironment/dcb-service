package org.olf.dcb.core.interaction.sierra;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class SierraHold {
	String statusCode;
	String statusName;
}
