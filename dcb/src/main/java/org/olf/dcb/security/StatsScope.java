package org.olf.dcb.security;

import io.micronaut.core.annotation.Nullable;

/**
 * The library filter Insights must actually apply, after the caller's token has had its
 * say. Never built from a query parameter alone.
 *
 * A record rather than a bare String because Reactor cannot carry null through a Mono, and
 * an empty Mono is far too easy to read as "denied" at a call site - which would fail open.
 * A null libraryCode means "no narrowing", and only a consortium-level caller ever gets one.
 */
public record StatsScope(@Nullable String libraryCode) {

	private static final StatsScope UNSCOPED = new StatsScope(null);

	public static StatsScope unscoped() {
		return UNSCOPED;
	}

	public static StatsScope of(String libraryCode) {
		return new StatsScope(libraryCode);
	}

	/**
	 * Several Host LMS codes, for a caller who administers more than one library.
	 * Comma-joined because that is what every Insights query already consumes -
	 * {@code = ANY(string_to_array(:libraryCode, ','))} - so one code and several take the
	 * same path with no second query shape to keep in step.
	 */
	public static StatsScope of(java.util.Collection<String> libraryCodes) {
		return new StatsScope(String.join(",", libraryCodes));
	}

	public boolean isUnscoped() {
		return libraryCode == null;
	}
}
