package org.olf.dcb.core.notifications;

import static org.olf.dcb.core.model.PatronRequest.Status.ERROR;
import static org.olf.dcb.core.model.PatronRequest.Status.NO_ITEMS_SELECTABLE_AT_ANY_AGENCY;

import org.olf.dcb.core.model.PatronRequest;

/**
 * A weekly figure a library is told about when it moves well outside its last eight weeks (S-23).
 * Each counts the requests that moved into a status during the week, from the audit trail, so a
 * past week's figure does not change as its requests move on. The threshold is part of the measure:
 * a measure cannot be added without one.
 */
public enum TrendMeasure {
	UNFILLED_BORROWING(NO_ITEMS_SELECTABLE_AT_ANY_AGENCY, HomeBucket.Scope.BORROWED, 2.0, 3),
	FAILED_SUPPLYING(ERROR, HomeBucket.Scope.SUPPLIED, 2.0, 3);

	private final PatronRequest.Status toStatus;
	private final HomeBucket.Scope scope;
	private final double ratio;
	private final int minimum;

	/**
	 * @param ratio how many times the trailing weekly average this week must reach
	 * @param minimum the fewest this week for it to count at all, so 0 to 1 is not a doubling
	 */
	TrendMeasure(PatronRequest.Status toStatus, HomeBucket.Scope scope, double ratio, int minimum) {
		if (ratio <= 1.0 || minimum < 1) {
			throw new IllegalArgumentException("A trend measure needs a ratio above 1 and a minimum of 1 or more");
		}
		this.toStatus = toStatus;
		this.scope = scope;
		this.ratio = ratio;
		this.minimum = minimum;
	}

	public PatronRequest.Status toStatus() {
		return toStatus;
	}

	public HomeBucket.Scope scope() {
		return scope;
	}

	/** Whether this week is well outside the trailing weeks: the alert, or none. */
	public boolean alerts(long thisWeek, double trailingAverage) {
		return thisWeek >= minimum && thisWeek >= ratio * trailingAverage;
	}
}
