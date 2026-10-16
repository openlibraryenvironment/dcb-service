package org.olf.dcb.request.fulfilment;

import java.time.Duration;

import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.Property;
import io.micronaut.core.annotation.Nullable;

/**
 * The bound on a placement's preflight checks together. Created at startup, so a blank or
 * non-positive setting stops the application rather than failing every placement after a green
 * rollout.
 */
@Context
public class PreflightChecksTimeout {
	static final String PROPERTY = "dcb.requests.preflight-checks.timeout";
	static final Duration DEFAULT = Duration.ofSeconds(55);

	private final Duration value;

	public PreflightChecksTimeout(@Property(name = PROPERTY) @Nullable Duration timeout,
		// The same key as text: a blank or unreadable value converts to no Duration
		@Property(name = PROPERTY) @Nullable String setting) {

		if (setting != null && (setting.isBlank() || timeout == null)) {
			throw new IllegalStateException(PROPERTY + " is \"" + setting + "\", which is not a duration. "
				+ "Remove it for the default of " + DEFAULT + ", or set one such as PT90S");
		}

		final var resolved = timeout != null ? timeout : DEFAULT;

		if (resolved.isZero() || resolved.isNegative()) {
			throw new IllegalStateException(PROPERTY + " must be positive: " + resolved);
		}

		this.value = resolved;
	}

	public Duration value() {
		return value;
	}
}
