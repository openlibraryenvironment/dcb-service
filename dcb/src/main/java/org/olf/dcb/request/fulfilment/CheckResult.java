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
	String notes;

	public static CheckResult passed() {
		return new CheckResult(true, null, null, null);
	}

	public static CheckResult passed(String notes) {
		return new CheckResult(true, null, null, notes);
	}

	public static CheckResult failed(String code, String description) {
		return new CheckResult(false, code, description, null);
	}

	public static CheckResult failed(String code, String description, String notes) {
		return new CheckResult(false, code, description, notes);
	}

	boolean getFailed() {
		return !getPassed();
	}
}
