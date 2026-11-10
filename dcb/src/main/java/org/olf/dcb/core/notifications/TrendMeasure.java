package org.olf.dcb.core.notifications;

import static org.olf.dcb.core.model.PatronRequest.Status.ERROR;
import static org.olf.dcb.core.model.PatronRequest.Status.NO_ITEMS_SELECTABLE_AT_ANY_AGENCY;

import org.olf.dcb.core.model.PatronRequest;

import io.micronaut.core.annotation.Nullable;

/**
 * A weekly figure a library is told about when it moves well outside its last eight weeks (S-23).
 * The threshold is part of the measure: a measure cannot be added without one.
 */
public enum TrendMeasure {
	UNFILLED_BORROWING(Source.AUDIT, NO_ITEMS_SELECTABLE_AT_ANY_AGENCY, HomeBucket.Scope.BORROWED, 2.0, 3),
	FAILED_SUPPLYING(Source.AUDIT, ERROR, HomeBucket.Scope.SUPPLIED, 2.0, 3),
	/** Hours from placing to ready for pickup; three days or more, and half again the usual. */
	MEDIAN_TURNAROUND_HOURS(Source.STORED, null, HomeBucket.Scope.BORROWED, 1.5, 72),
	/** The library's patrons' items on a hold shelf when the week ends. */
	AWAITING_PICKUP(Source.STORED, null, HomeBucket.Scope.BORROWED, 2.0, 5);

	public enum Source {
		/** Requests moving into a status in the week, so a past week can be counted again at any time. */
		AUDIT,
		/** A figure that cannot be rebuilt later, recorded each week by WeeklyMeasureJob into library_weekly_measure. */
		STORED
	}

	private final Source source;
	private final PatronRequest.Status toStatus;
	private final HomeBucket.Scope scope;
	private final double ratio;
	private final double minimum;

	/**
	 * @param ratio how many times the trailing weekly average this week must reach
	 * @param minimum the least this week must be for it to count at all, so 0 to 1 is not a doubling
	 */
	TrendMeasure(Source source, @Nullable PatronRequest.Status toStatus, HomeBucket.Scope scope, double ratio,
		double minimum) {

		if (ratio <= 1.0 || minimum < 1) {
			throw new IllegalArgumentException("A trend measure needs a ratio above 1 and a minimum of 1 or more");
		}
		if ((source == Source.AUDIT) != (toStatus != null)) {
			throw new IllegalArgumentException("An audit measure names the status it counts; a stored one does not");
		}
		this.source = source;
		this.toStatus = toStatus;
		this.scope = scope;
		this.ratio = ratio;
		this.minimum = minimum;
	}

	public Source source() {
		return source;
	}

	public PatronRequest.Status toStatus() {
		return toStatus;
	}

	public HomeBucket.Scope scope() {
		return scope;
	}

	/** Whether this week is well outside the trailing weeks: the alert, or none. */
	public boolean alerts(double thisWeek, double trailingAverage) {
		return thisWeek >= minimum && thisWeek >= ratio * trailingAverage;
	}
}
