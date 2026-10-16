package org.olf.dcb.indexing.model;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import io.micronaut.core.annotation.Nullable;

/**
 * A classification turned into a fixed-width key that sorts bytewise in shelf order, or no key
 * at all when it cannot be read. Why each choice: docs/shared-index.md, "Shelf sort keys".
 */
public final class ShelfSortKey {

	private ShelfSortKey() {
	}

	/** MARC 082 and the label MarcIngestSource gives it. */
	public static final String DEWEY = "dewey";

	/** MARC 050. */
	public static final String LCC = "lcc";

	private static final Pattern DEWEY_NUMBER =
		Pattern.compile("^(\\d{1,3})(?:\\.(\\d+))?");

	private static final Pattern LCC_NUMBER =
		Pattern.compile("^([A-Z]{1,3})\\s*(\\d{1,4})(?:\\.(\\d+))?(.*)$");

	private static final Pattern LCC_CUTTER =
		Pattern.compile("\\.?([A-Z])(\\d{1,4})");

	/** Decimal places kept. Beyond six the shelf does not distinguish them either. */
	private static final int DECIMALS = 6;

	/**
	 * The sort key for one classification, or null when it cannot be read confidently.
	 *
	 * {@code scheme} is the {@code subtype} MarcIngestSource records: dewey, lcc or other.
	 * {@code other} deliberately produces no key — MARC 084 names its scheme in $2 and can
	 * carry anything from a local shelving code to a national scheme, and normalising an
	 * unknown scheme by guessing at its shape is the failure this class exists to avoid.
	 */
	@Nullable
	public static String of(@Nullable String scheme, @Nullable String label) {
		if (scheme == null || label == null) {
			return null;
		}

		return switch (scheme.toLowerCase(Locale.ROOT)) {
			case DEWEY -> dewey(label);
			case LCC -> lcc(label);
			default -> null;
		};
	}

	/**
	 * Dewey: three digits, a point, six decimal places. {@code 638.1} to {@code 638.100000}.
	 *
	 * Left-padded with zeros rather than spaces because a Dewey class is always three digits
	 * on the shelf — {@code 05} is shelved as {@code 005}, and a catalogue that recorded it
	 * short meant the same place.
	 */
	@Nullable
	static String dewey(String label) {
		// MARC 082 marks segmentation inside the number with / and ', as in 823/.914.
		final var trimmed = trim(label.replace("/", "").replace("'", ""));
		if (trimmed == null) {
			return null;
		}

		final var matcher = DEWEY_NUMBER.matcher(trimmed);
		if (!matcher.find()) {
			return null;
		}

		return "%03d.%s".formatted(
			Integer.parseInt(matcher.group(1)), decimals(matcher.group(2)));
	}

	/**
	 * LCC: class letters, a four-digit number with decimals, then the cutters in order.
	 *
	 * {@code PS3566.Y55 A6 2000} becomes {@code "PS  3566.000000 Y5500 A6000"}.
	 *
	 * The letters are right-padded with SPACES, not zeros, and that is the whole reason this
	 * sorts correctly: space (0x20) is below every letter, so {@code P} precedes {@code PA}
	 * precedes {@code PS}, which is shelf order. Zero-padding would put them in a different
	 * order from the shelf, and a plausible one.
	 */
	@Nullable
	static String lcc(String label) {
		final var trimmed = trim(label);
		if (trimmed == null) {
			return null;
		}

		final var matcher = LCC_NUMBER.matcher(trimmed.toUpperCase(Locale.ROOT));
		if (!matcher.matches()) {
			return null;
		}

		final var key = new StringBuilder()
			.append("%-3s".formatted(matcher.group(1)))
			.append(" ")
			.append("%04d.%s".formatted(
				Integer.parseInt(matcher.group(2)), decimals(matcher.group(3))));

		// The cutters, each letter plus a four-digit number. A cutter is read as a DECIMAL
		// fraction on the shelf — .Y55 sits before .Y6 — so it is right-padded with zeros
		// to a constant width rather than left-padded like the class number.
		final var cutters = LCC_CUTTER.matcher(matcher.group(4));
		while (cutters.find()) {
			key.append(" ")
				.append(cutters.group(1))
				.append("%-4s".formatted(cutters.group(2)).replace(' ', '0'));
		}

		return key.toString();
	}

	/**
	 * Every classification a bib declares, by scheme, keeping the first of each.
	 *
	 * First rather than last, and rather than all: a record with two Dewey numbers has one
	 * place on a shelf, and picking a different one per index pass would move records about
	 * between reindexes for no reason a reader could see.
	 */
	static void collect(@Nullable Object classifications, Map<String, String> labels,
		Map<String, String> sortKeys) {

		if (!(classifications instanceof Iterable<?> entries)) {
			return;
		}

		for (final var entry : entries) {
			if (!(entry instanceof Map<?, ?> fields)) {
				continue;
			}

			final var scheme = string(fields.get("subtype"));
			final var label = trim(string(fields.get("label")));

			if (scheme == null || label == null || labels.containsKey(scheme)) {
				continue;
			}

			labels.put(scheme, label);

			final var key = of(scheme, label);
			if (key != null) {
				sortKeys.put(scheme, key);
			}
		}
	}

	private static String decimals(@Nullable String raw) {
		final var digits = raw == null ? "" : raw;

		return digits.length() >= DECIMALS
			? digits.substring(0, DECIMALS)
			: digits + "0".repeat(DECIMALS - digits.length());
	}

	@Nullable
	private static String string(@Nullable Object value) {
		return value == null ? null : value.toString();
	}

	@Nullable
	private static String trim(@Nullable String value) {
		if (value == null) {
			return null;
		}

		final var trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}
}
