package org.olf.dcb.core.interaction.polaris;

import java.time.Duration;
import java.time.Instant;
import java.util.regex.Pattern;

import lombok.extern.slf4j.Slf4j;

/**
 * Parsing for the Microsoft JSON date format Polaris returns, e.g. {@code /Date(1787240106747-0500)/}.
 *
 * The value is epoch milliseconds; the trailing offset is a display hint only and does not shift
 * the instant, so it is matched but not applied. It is optional here because not every Polaris
 * response includes it.
 *
 * Extracted from PolarisLmsClient so the auth filters can share it - the token expiry
 * (AuthExpDate) arrives in exactly this format.
 */
@Slf4j
final class PolarisDates {
	private static final Pattern MS_JSON_DATE =
		Pattern.compile("/date\\((\\d+)(?:([+-]\\d{4}))?\\)/");

	/** Renew this long before the stated expiry, so a token cannot lapse mid-flight. */
	static final Duration EXPIRY_SAFETY_MARGIN = Duration.ofSeconds(60);

	private PolarisDates() {}

	/**
	 * @return the instant, or null if the value is absent or not a Microsoft JSON date.
	 *         Never throws - a bad date must not fail the request that carried it.
	 */
	static Instant parseMsJsonDate(String msDate) {
		if (msDate == null) return null;

		try {
			final var matcher = MS_JSON_DATE.matcher(msDate.toLowerCase());

			if (matcher.matches()) {
				return Instant.ofEpochMilli(Long.parseLong(matcher.group(1)));
			}

			log.warn("Invalid Microsoft date format: {}", msDate);
		}
		catch (Exception e) {
			log.warn("Problem parsing polaris date: {}:{}", msDate, e.getMessage());
		}

		return null;
	}

	/**
	 * How long a token may be reused, from the AuthExpDate it was issued with.
	 *
	 * @return the remaining validity less a safety margin, or null when that cannot be
	 *         established - an absent, unparseable or already-past expiry. Callers fall back to
	 *         their configured ceiling rather than treating null as "do not cache".
	 */
	static Duration ttlFromAuthExpDate(String authExpDate) {
		final var expiry = parseMsJsonDate(authExpDate);

		if (expiry == null) return null;

		final var remaining = Duration.between(Instant.now(), expiry).minus(EXPIRY_SAFETY_MARGIN);

		return remaining.isPositive() ? remaining : null;
	}
}
