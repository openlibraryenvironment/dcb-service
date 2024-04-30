package org.olf.dcb.request.fulfilment;

import java.util.List;

import lombok.EqualsAndHashCode;
import lombok.Value;

@EqualsAndHashCode(callSuper = true)
@Value
public class PreflightCheckFailedException extends RuntimeException {
	List<FailedPreflightCheck> failedChecks;

	public PreflightCheckFailedException(List<FailedPreflightCheck> failedChecks) {
		super("Preflight checks failed: %s".formatted(failedChecks));

		this.failedChecks = failedChecks;
	}
}
