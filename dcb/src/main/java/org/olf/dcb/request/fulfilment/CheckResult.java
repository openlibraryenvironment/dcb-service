package org.olf.dcb.request.fulfilment;

import static lombok.AccessLevel.PRIVATE;

import lombok.AllArgsConstructor;
import lombok.Value;

@Value
@AllArgsConstructor(access = PRIVATE)
public class CheckResult {
	Boolean passed;
	String failureCode;
	String failureDescription;

	public static CheckResult passed() {
		return new CheckResult(true, null, null);
	}

	public static CheckResult failed(String code, String description) {
		return new CheckResult(false, code, description);
	}

	boolean getFailed() {
		return !getPassed();
	}
}
