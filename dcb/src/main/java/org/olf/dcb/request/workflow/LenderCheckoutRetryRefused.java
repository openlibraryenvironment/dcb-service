package org.olf.dcb.request.workflow;

import java.time.Duration;

public class LenderCheckoutRetryRefused extends RuntimeException {
	private final String code;
	private final transient Duration retryAfter;

	LenderCheckoutRetryRefused(String code, String message, Duration retryAfter) {
		super(message);
		this.code = code;
		this.retryAfter = retryAfter;
	}

	public String getCode() {
		return code;
	}

	public Duration getRetryAfter() {
		return retryAfter;
	}
}
