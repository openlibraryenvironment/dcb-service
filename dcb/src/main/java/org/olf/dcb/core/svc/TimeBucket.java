package org.olf.dcb.core.svc;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * The bucket widths every DCB time series supports, and the one place the window contract is
 * defined. Shared by the Insights flow time series and the Audit Explorer's incidence chart, so
 * two charts on one dashboard cannot differ by a bucket. Rationale: docs/insights.md part 3.
 *
 * <p>The contract, all four parts of which callers must honour:
 *
 * <ol>
 *   <li>{@code start} inclusive, {@code end} exclusive, so consecutive windows tile.</li>
 *   <li>Capped at {@link #MAX_BUCKETS} and REJECTED past it, never truncated - fetch
 *       {@code MAX_BUCKETS + 1} rows and reject via {@link #tooManyBuckets()}.</li>
 *   <li>Gap filled server side: an empty bucket is a zero, not an omission.</li>
 *   <li>Unknown bucket names are rejected, never defaulted to DAY.</li>
 * </ol>
 *
 * <p>Timestamps are {@code timestamp} WITHOUT time zone holding UTC, so {@code date_trunc} is
 * already UTC and must NOT be given {@code AT TIME ZONE 'UTC'} - that reinterprets the value
 * against the session zone and moves every bucket boundary.
 */
public enum TimeBucket {

	HOUR("hour"),
	DAY("day"),
	WEEK("week"),
	MONTH("month");

	/** Without this, HOUR over a multi-year window asks generate_series for tens of thousands. */
	public static final int MAX_BUCKETS = 1000;

	/** Safe to interpolate because it is chosen here, never supplied by the caller. */
	private final String dateTruncUnit;

	TimeBucket(String dateTruncUnit) {
		this.dateTruncUnit = dateTruncUnit;
	}

	public String getDateTruncUnit() {
		return dateTruncUnit;
	}

	/**
	 * @param name a bucket name, case insensitive. Null or blank means DAY.
	 * @throws IllegalArgumentException if the name is not a supported width - the point of the
	 *   type: a typo becomes an error rather than a misleading chart.
	 */
	public static TimeBucket fromName(String name) {
		if (name == null || name.isBlank()) {
			return DAY;
		}

		try {
			return valueOf(name.trim().toUpperCase());
		}
		catch (IllegalArgumentException cause) {
			throw new IllegalArgumentException(
				"Unknown time bucket \"%s\". Expected one of %s".formatted(name, names()), cause);
		}
	}

	/** Shared so both time series reject an over long window identically. */
	public IllegalArgumentException tooManyBuckets() {
		return new IllegalArgumentException(
			("This window needs more than %d %s buckets. Widen the interval or narrow the window.")
				.formatted(MAX_BUCKETS, name()));
	}

	private static String names() {
		return Arrays.stream(values()).map(Enum::name).collect(Collectors.joining(", "));
	}
}
