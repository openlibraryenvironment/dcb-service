package org.olf.dcb.request.fulfilment;

import io.micronaut.serde.annotation.Serdeable;
import lombok.Builder;
import lombok.Value;

@Value
@Serdeable
@Builder
public class FailedPreflightCheck {
	String failureDescription;
}
