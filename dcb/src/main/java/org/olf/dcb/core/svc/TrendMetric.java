package org.olf.dcb.core.svc;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * The durations {@code /insights/trend} will plot over time, as a FIXED VOCABULARY.
 *
 * <p>A metric name reaches a native query, so it is never a column name the caller supplied and
 * never free text: it is an id from this list, and the query behind it is chosen here. Unknown
 * names are rejected rather than defaulted, for the reason {@link TimeBucket#fromName} gives.
 *
 * <p>Each one buckets on when the duration ENDED, so a bucket is complete once it closes. Rationale
 * and the two edge effects that remain: {@code docs/insights.md} part 3.9.
 */
public enum TrendMetric {

	/** Request creation to first reaching a target status. Scoped on the borrowing library. */
	TURNAROUND_TO_STATUS,

	/**
	 * Placed at the supplier to confirmed by it. Scoped on the SUPPLYING library, because this
	 * is the caller's performance as a lender; scoping it on the borrower would answer a
	 * different question under the same name.
	 */
	SUPPLIER_RESPONSE,

	/** Median dwell in one status, which is where both transit legs live. Borrower scoped. */
	STATUS_DWELL;

	/**
	 * @param name a metric id, case insensitive. Null or blank means TURNAROUND_TO_STATUS.
	 * @throws IllegalArgumentException if the name is not one of the supported metrics.
	 */
	public static TrendMetric fromName(String name) {
		if (name == null || name.isBlank()) {
			return TURNAROUND_TO_STATUS;
		}

		try {
			return valueOf(name.trim().toUpperCase());
		}
		catch (IllegalArgumentException cause) {
			throw new IllegalArgumentException(
				"Unknown trend metric \"%s\". Expected one of %s".formatted(name, names()), cause);
		}
	}

	private static String names() {
		return Arrays.stream(values()).map(Enum::name).collect(Collectors.joining(", "));
	}
}
