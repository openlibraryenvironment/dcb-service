package org.olf.dcb.request.fulfilment;

import java.util.List;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;

@EqualsAndHashCode(callSuper = true)
@Value
@Builder
public class PreflightCheckFailedException extends RuntimeException {
	List<FailedPreflightCheck> failedChecks;

	static PreflightCheckFailedException from(List<CheckResult> results) {
		final var failedChecks = results.stream()
			.filter(CheckResult::getFailed)
			.map(FailedPreflightCheck::fromResult)
			.toList();

		return builder()
			.failedChecks(failedChecks)
			.build();
	}
}
