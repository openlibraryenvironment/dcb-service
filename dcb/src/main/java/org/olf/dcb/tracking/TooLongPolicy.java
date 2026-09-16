package org.olf.dcb.tracking;

import java.time.Duration;
import java.time.Instant;

import org.olf.dcb.core.model.PatronRequest;

/**
 * When a request stuck in a non-terminal state stops being visited by automatic tracking.
 */
public final class TooLongPolicy {
	public static final int THRESHOLD_DAYS = 56;

	private TooLongPolicy() {
	}

	/**
	 * Measured from the later of the last status change and a manual resume, so a request somebody has
	 * asked DCB to check again is tracked for a full threshold before it is parked once more.
	 * currentStatusTimestamp cannot carry that: staleRequests.sql and requestsByLocalStatus.sql read it.
	 */
	public static boolean hasBeenInCurrentStatusTooLong(PatronRequest patronRequest) {
		final var lastStatusChange = patronRequest.getCurrentStatusTimestamp();

		if (lastStatusChange == null) {
			return false;
		}

		final var threshold = Instant.now().minus(Duration.ofDays(THRESHOLD_DAYS));
		final var resumedAt = patronRequest.getTrackingResumedAt();

		return lastStatusChange.isBefore(threshold)
			&& (resumedAt == null || resumedAt.isBefore(threshold));
	}
}
