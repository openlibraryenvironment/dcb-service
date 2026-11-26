package org.olf.dcb.request.fulfilment;

import java.time.Duration;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpStatus;

/** Why DCB did not remove a virtual patron, with the HTTP status and a code the caller can act on. */
public class VirtualPatronRemovalRefused extends RuntimeException {
	private final HttpStatus status;
	private final String code;
	@Nullable
	private final transient Duration retryAfter;

	VirtualPatronRemovalRefused(HttpStatus status, String code, String message) {
		this(status, code, message, null);
	}

	VirtualPatronRemovalRefused(HttpStatus status, String code, String message, @Nullable Duration retryAfter) {
		super(message);
		this.status = status;
		this.code = code;
		this.retryAfter = retryAfter;
	}

	public HttpStatus getStatus() {
		return status;
	}

	public String getCode() {
		return code;
	}

	@Nullable
	public Duration getRetryAfter() {
		return retryAfter;
	}
}
