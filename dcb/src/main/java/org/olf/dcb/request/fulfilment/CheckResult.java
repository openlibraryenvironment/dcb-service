package org.olf.dcb.request.fulfilment;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Value;

@Value
@AllArgsConstructor(access = AccessLevel.PRIVATE)
class CheckResult {
	Boolean passed;
	String failureDescription;

	public static CheckResult passed() {
		return new CheckResult(true, "");
	}

	public static CheckResult failed(String description) {
		return new CheckResult(false, description);
	}
}
