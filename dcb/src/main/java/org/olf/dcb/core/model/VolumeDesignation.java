package org.olf.dcb.core.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * One comparable form for a volume statement, whichever Host LMS or client it came from.
 * <p>
 * A statement made only of recognised parts becomes those parts in the fixed order
 * volume, part, number, year: "Vol. 2, pt. 1" and "Vol [ 2 ] Pt [ 1 ]" are both "v2p1",
 * and "1998 v.3" is "v3y1998". Anything else is lowercased with punctuation and spaces
 * removed, so two identical statements still compare equal.
 */
public final class VolumeDesignation {
	private static final Pattern TOKEN = Pattern.compile("\\p{L}+|\\d+");
	private static final Pattern SEPARATORS = Pattern.compile("[\\p{Punct}\\p{Z}\\s]+");
	private static final Pattern YEAR = Pattern.compile("[12]\\d{3}");

	private static final int VOLUME = 0;
	private static final int PART = 1;
	private static final int NUMBER = 2;
	private static final int YEAR_PART = 3;
	private static final String[] PREFIXES = { "v", "p", "n", "y" };

	private static final Map<String, Integer> LABELS = Map.of(
		"v", VOLUME, "vol", VOLUME, "volume", VOLUME,
		"p", PART, "pt", PART, "part", PART,
		"n", NUMBER, "no", NUMBER, "number", NUMBER);

	private VolumeDesignation() { }

	/** The canonical form of a statement, or null when there is nothing to compare */
	public static String normalise(String statement) {
		if (statement == null || statement.isBlank()) {
			return null;
		}

		final var lowerCase = statement.toLowerCase(Locale.ROOT);

		return recognisedParts(lowerCase).orElseGet(() -> withoutPunctuation(lowerCase));
	}

	private static Optional<String> recognisedParts(String statement) {
		final List<String> tokens = new ArrayList<>();
		TOKEN.matcher(statement).results().forEach(match -> tokens.add(match.group()));

		final var parts = new String[PREFIXES.length];

		for (int index = 0; index < tokens.size(); index++) {
			final var token = tokens.get(index);
			final var label = LABELS.get(token);

			if (label != null) {
				final var next = index + 1 < tokens.size() ? tokens.get(index + 1) : null;

				if (parts[label] != null || next == null || !isNumber(next)) {
					return Optional.empty();
				}

				parts[label] = withoutLeadingZeros(next);
				index++;
			}
			else if (YEAR.matcher(token).matches() && parts[YEAR_PART] == null) {
				parts[YEAR_PART] = token;
			}
			else {
				return Optional.empty();
			}
		}

		final var canonical = new StringBuilder();

		for (int part = 0; part < parts.length; part++) {
			if (parts[part] != null) {
				canonical.append(PREFIXES[part]).append(parts[part]);
			}
		}

		return canonical.isEmpty() ? Optional.empty() : Optional.of(canonical.toString());
	}

	// Digits either side of a removed separator keep a hyphen, so "v.1-2" cannot become "v12"
	private static String withoutPunctuation(String statement) {
		final var result = new StringBuilder();

		for (String piece : SEPARATORS.split(statement)) {
			if (piece.isEmpty()) {
				continue;
			}

			if (!result.isEmpty() && Character.isDigit(result.charAt(result.length() - 1))
				&& Character.isDigit(piece.charAt(0))) {

				result.append('-');
			}

			result.append(piece);
		}

		return result.isEmpty() ? null : result.toString();
	}

	private static boolean isNumber(String token) {
		return Character.isDigit(token.charAt(0));
	}

	private static String withoutLeadingZeros(String number) {
		return number.replaceFirst("^0+(?=\\d)", "");
	}
}
