package org.olf.dcb.request.fulfilment;

import static io.micronaut.core.util.CollectionUtils.isEmpty;

import java.util.List;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;

@EqualsAndHashCode(callSuper = true)
@Value
public class PreflightCheckFailedException extends RuntimeException {
	List<FailedPreflightCheck> failedChecks;

	private PreflightCheckFailedException(List<FailedPreflightCheck> failedChecks) {
		super("Preflight checks failed: %s".formatted(failedChecks));

		this.failedChecks = failedChecks;
	}

	static PreflightCheckFailedException from(List<CheckResult> results) {
		final var failedChecks = results.stream()
			.filter(CheckResult::getFailed)
			.map(FailedPreflightCheck::fromResult)
			.toList();

		return new PreflightCheckFailedException(failedChecks);
	}
}
