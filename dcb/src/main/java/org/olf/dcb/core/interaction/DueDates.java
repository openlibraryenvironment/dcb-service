package org.olf.dcb.core.interaction;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;

import io.micronaut.core.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;

/**
 * Turns a Host LMS due date into an {@link Instant}, or into nothing.
 *
 * <p>Two formats, both observed in this codebase rather than guessed at: ISO-8601, which is
 * what Sierra and Koha send, and Polaris Application Services' {@code MMM d yyyy h:mma},
 * which {@code PolarisItemMapper} already parses on the availability path.
 *
 * <p>An unparseable date returns null, and null means UNKNOWN. It never means "not on loan":
 * a patron shown "no due date" for something they are holding would plan around it.
 */
@Slf4j
public final class DueDates {

	// Polaris' own spacing is irregular - "Sep  3 2026  4:00PM" - which is why the optional
	// spaces are in the pattern rather than trimmed out of the input.
	private static final DateTimeFormatter POLARIS =
		DateTimeFormatter.ofPattern("MMM[ ][ ][d] yyyy [ ]h:[ ]ma", Locale.ENGLISH);

	private DueDates() {
	}

	@Nullable
	public static Instant parse(@Nullable String dueDate) {
		if (dueDate == null || dueDate.isBlank()) {
			return null;
		}

		try {
			return Instant.parse(dueDate);
		}
		catch (DateTimeParseException notIso) {
			return parsePolaris(dueDate);
		}
	}

	@Nullable
	private static Instant parsePolaris(String dueDate) {
		try {
			return LocalDateTime.parse(dueDate, POLARIS).toInstant(ZoneOffset.UTC);
		}
		catch (DateTimeParseException unrecognised) {
			// Debug rather than warn: this runs per item on a tracking sweep, and a Host LMS
			// that formats dates a third way would otherwise fill the log at the rate of the
			// sweep. The consequence a reader needs is visible anyway - the date is unknown.
			log.debug("Could not read a due date from '{}'", dueDate);
			return null;
		}
	}
}
