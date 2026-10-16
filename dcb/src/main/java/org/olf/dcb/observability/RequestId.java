package org.olf.dcb.observability;

import java.util.UUID;
import java.util.regex.Pattern;

import io.micronaut.core.annotation.Nullable;

/**
 * The id that ties a caller's log line to this service's, carried in {@link #HEADER}.
 * A discovery service sends the reference its patron was shown. See
 * docs/request-id.md.
 */
public final class RequestId {

	public static final String HEADER = "X-Request-Id";

	/** The MDC key, and therefore the {@code %X{requestId}} in logback.xml. */
	public static final String MDC_KEY = "requestId";

	// The caller controls this and it is written into log lines: a line break would forge one.
	private static final Pattern ACCEPTABLE = Pattern.compile("[A-Za-z0-9._-]{1,64}");

	private RequestId() {
	}

	public static String resolve(@Nullable String supplied) {
		return supplied != null && ACCEPTABLE.matcher(supplied).matches() ? supplied : generate();
	}

	public static String generate() {
		return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
	}
}
