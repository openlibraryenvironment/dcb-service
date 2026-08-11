package org.olf.dcb.request.lifecycle.ncip.profile.application;

import io.micronaut.http.HttpStatus;

public class DcbProfileRegistrationException extends RuntimeException {
	private final HttpStatus status;
	private final String code;
	private final String field;
	private final String prerequisite;
	private final boolean retryable;

	public DcbProfileRegistrationException(
		HttpStatus status,
		String code,
		String message,
		String field,
		String prerequisite,
		boolean retryable
	) {
		super(message);
		this.status = status;
		this.code = code;
		this.field = field;
		this.prerequisite = prerequisite;
		this.retryable = retryable;
	}

	public static DcbProfileRegistrationException invalid(String code, String message, String field) {
		return new DcbProfileRegistrationException(
			HttpStatus.BAD_REQUEST, code, message, field, code, false);
	}

	public static DcbProfileRegistrationException unauthorized(String code, String message) {
		return new DcbProfileRegistrationException(
			HttpStatus.UNAUTHORIZED, code, message, null, code, false);
	}

	public static DcbProfileRegistrationException conflict(String code, String message, String field) {
		return new DcbProfileRegistrationException(
			HttpStatus.CONFLICT, code, message, field, code, false);
	}

	public static DcbProfileRegistrationException unavailable(String code, String message) {
		return new DcbProfileRegistrationException(
			HttpStatus.BAD_GATEWAY, code, message, null, code, true);
	}

	public static DcbProfileRegistrationException notReady(String code, String message) {
		return new DcbProfileRegistrationException(
			HttpStatus.SERVICE_UNAVAILABLE, code, message, null, code, true);
	}

	public HttpStatus status() {
		return status;
	}

	public String code() {
		return code;
	}

	public String field() {
		return field;
	}

	public String prerequisite() {
		return prerequisite;
	}

	public boolean retryable() {
		return retryable;
	}
}
