package org.olf.dcb.core.model;

import java.util.Locale;

import io.micronaut.core.annotation.Nullable;

/**
 * Which classification a library shelves by: an allow-list, because the value will choose
 * which {@code members[].shelfSort.*} field a browse query sorts on. Only the two schemes
 * {@link ShelfSortKey} can normalise; not stated means no shelf browse. Why:
 * docs/shared-index.md, "Shelf sort keys".
 */
public enum ClassificationScheme {
	DEWEY,
	LCC;

	/**
	 * The scheme a library declared, or null when it declared none or something unknown.
	 *
	 * Null for an unknown value rather than an error: the column is a varchar any SQL client can
	 * write, so such a row must degrade to "no shelf browse here" rather than fail the library's
	 * whole record on read.
	 */
	@Nullable
	public static ClassificationScheme parse(@Nullable String raw) {
		if (raw == null || raw.isBlank()) {
			return null;
		}

		try {
			return valueOf(raw.trim().toUpperCase(Locale.ROOT));
		}
		catch (IllegalArgumentException e) {
			return null;
		}
	}

	/** The index field this scheme's shelf order is carried in. */
	public String shelfSortField() {
		return "members.shelfSort." + name().toLowerCase(Locale.ROOT);
	}
}
