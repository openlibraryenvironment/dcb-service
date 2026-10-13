package org.olf.dcb.core.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
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

	// "y" is the year's own prefix, so a canonical form reads back into its parts
	private static final Map<String, Integer> LABELS = Map.of(
		"v", VOLUME, "vol", VOLUME, "volume", VOLUME,
		"p", PART, "pt", PART, "part", PART,
		"n", NUMBER, "no", NUMBER, "number", NUMBER,
		"y", YEAR_PART);

	// A MARC 300 extent of more than one volume: "3 v.", "10v.", "12 vols", "v. 1-4", or an open
	// entry with no count, "v. :", "v.ill.", "volumes <1-5> :" or "(volumes)". A "v" before a page
	// count is a roman page number: "v, 423 p." and "v. 423 p." are one volume
	private static final Pattern SEVERAL_VOLUMES = Pattern.compile(
		"(?<!\\d)(?:[2-9]|[1-9]\\d+)\\s*(?:v|vol|vols|volumes?)\\b"
			+ "|\\bv\\.\\s*\\d+\\s*-\\s*\\d+"
			+ "|^\\s*(?:v\\.(?!\\s*\\d)|v\\s*(?:[:;(<]|$)|volumes?\\b(?!\\s*\\d))"
			+ "|\\(\\s*volumes\\s*\\)",
		Pattern.CASE_INSENSITIVE);

	// "2 v. in 1": several volumes bound as one item
	private static final Pattern BOUND_AS_ONE = Pattern.compile("\\bin\\s+1\\b", Pattern.CASE_INSENSITIVE);

	private VolumeDesignation() { }

	/** Whether a physical description (a MARC 300 extent) is of more than one volume */
	public static boolean describesSeveralVolumes(String physicalDescription) {
		return physicalDescription != null
			&& SEVERAL_VOLUMES.matcher(physicalDescription).find()
			&& !BOUND_AS_ONE.matcher(physicalDescription).find();
	}

	/** The canonical volume a copy states: its parsed statement, else its raw one, or null when it states none */
	public static String of(Item item) {
		if (item == null) {
			return null;
		}

		final var parsed = normalise(item.getParsedVolumeStatement());

		return parsed != null ? parsed : normalise(item.getRawVolumeStatement());
	}

	/** The canonical form of a statement, or null when there is nothing to compare */
	public static String normalise(String statement) {
		if (statement == null || statement.isBlank()) {
			return null;
		}

		final var lowerCase = statement.toLowerCase(Locale.ROOT);

		return partsOf(lowerCase).map(VolumeDesignation::joined).orElseGet(() -> withoutPunctuation(lowerCase));
	}

	/**
	 * Whether a copy stating copyStatement is the volume a request names. When both are made of
	 * recognised parts, volume, part and number must be the same on both, and the year must be the
	 * same if the request names one: a copy may add a year, which describes the volume, but not a
	 * part or number, which would make it a fragment of it. So a request for "v.22" is met by a copy
	 * stating "Vol [ 22].(1990)", and not by "v.22 pt.1" or "v.22 1991". Otherwise only the same
	 * statement matches.
	 */
	public static boolean matches(String requestedStatement, String copyStatement) {
		final var requested = normalise(requestedStatement);
		final var copy = normalise(copyStatement);

		if (requested == null || copy == null) {
			return false;
		}

		final var requestedParts = partsOf(requested);
		final var copyParts = partsOf(copy);

		if (requestedParts.isEmpty() || copyParts.isEmpty()) {
			return requested.equals(copy);
		}

		for (int part = 0; part < PREFIXES.length; part++) {
			final var wanted = requestedParts.get()[part];
			final var stated = copyParts.get()[part];

			final var differs = part == YEAR_PART
				? wanted != null && !wanted.equals(stated)
				: !Objects.equals(wanted, stated);

			if (differs) {
				return false;
			}
		}

		return true;
	}

	private static String joined(String[] parts) {
		final var canonical = new StringBuilder();

		for (int part = 0; part < parts.length; part++) {
			if (parts[part] != null) {
				canonical.append(PREFIXES[part]).append(parts[part]);
			}
		}

		return canonical.toString();
	}

	private static Optional<String[]> partsOf(String statement) {
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

		for (String part : parts) {
			if (part != null) {
				return Optional.of(parts);
			}
		}

		return Optional.empty();
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
