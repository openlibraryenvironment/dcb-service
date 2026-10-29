package org.olf.dcb.preferences;

import io.micronaut.http.HttpStatus;

/** A refused preference request, with a reason the caller can act on: PreferenceExceptionHandler. */
public class PreferenceException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	private final HttpStatus status;
	private final String code;

	private PreferenceException(HttpStatus status, String code, String message) {
		super(message);

		this.status = status;
		this.code = code;
	}

	public HttpStatus status() {
		return status;
	}

	public String code() {
		return code;
	}

	public static PreferenceException invalid(String message) {
		return new PreferenceException(HttpStatus.BAD_REQUEST, "INVALID_PREFERENCE", message);
	}

	public static PreferenceException noSubject(String message) {
		return new PreferenceException(HttpStatus.BAD_REQUEST, "NO_SUBJECT", message);
	}

	public static PreferenceException quotaReached(String message) {
		return new PreferenceException(HttpStatus.CONFLICT, "QUOTA_REACHED", message);
	}

	public static PreferenceException duplicateName(String message) {
		return new PreferenceException(HttpStatus.CONFLICT, "DUPLICATE_NAME", message);
	}

	public static PreferenceException notFound(String message) {
		return new PreferenceException(HttpStatus.NOT_FOUND, "NOT_FOUND", message);
	}

	/** 503, not 404: on this API 404 means "nothing stored yet", and a client must tell the two apart. */
	public static PreferenceException unavailable() {
		return new PreferenceException(HttpStatus.SERVICE_UNAVAILABLE, "PREFERENCES_UNAVAILABLE",
			"This deployment does not store staff preferences, so settings stay in this browser.");
	}
}
