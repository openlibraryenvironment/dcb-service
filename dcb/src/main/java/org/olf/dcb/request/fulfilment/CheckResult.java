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
	String userMessage;

	public static CheckResult passed() {
		return new CheckResult(true, null, null, null, null);
	}

	public static CheckResult passed(String notes) {
		return new CheckResult(true, null, null, notes, null);
	}

	public static CheckResult failedUm(String code, String description, String userMessage) {
		return new CheckResult(false, code, description, null, userMessage);
	}

	public static CheckResult failed(String code, String description, String notes) {
		return new CheckResult(false, code, description, notes, null);
	}

	public static CheckResult failed(String code, String description, String notes, String userMessage) {
		return new CheckResult(false, code, description, notes, userMessage);
	}

	boolean getFailed() {
		return !getPassed();
	}
}
