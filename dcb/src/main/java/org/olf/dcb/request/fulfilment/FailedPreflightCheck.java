package org.olf.dcb.request.fulfilment;

import io.micronaut.serde.annotation.Serdeable;
import lombok.Builder;
import lombok.Value;

@Value
@Serdeable
@Builder
public class FailedPreflightCheck {
	String code;
	String description;
  String notes;
  String userMessage;


	static FailedPreflightCheck fromResult(CheckResult result) {
		return builder()
			.code(result.getFailureCode())
			.description(result.getFailureDescription())
			.notes(result.getNotes())
			.userMessage(result.getUserMessage())
			.build();
	}
}
