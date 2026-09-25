package org.olf.dcb.tracking;

import java.time.Duration;
import java.time.Instant;

import org.olf.dcb.core.model.PatronRequest;

import io.micronaut.context.annotation.Value;
import jakarta.inject.Singleton;

/**
 * When a request stuck in a non-terminal state stops being visited by automatic tracking.
 *
 * <p>Configurable, because 56 days suits some consortia and not others:
 * {@code dcb.tracking.too-long}, so {@code DCB_TRACKING_TOO_LONG=90d}.
 */
@Singleton
public class TooLongPolicy {
	private final Duration threshold;

	public TooLongPolicy(@Value("${dcb.tracking.too-long:56d}") Duration threshold) {
		this.threshold = threshold;
	}

	/** For the audit entries, which tell an operator how long "too long" was on this deployment. */
	public Duration threshold() {
		return threshold;
	}

	/**
	 * Measured from the later of the last status change and a manual resume, so a request somebody has
	 * asked DCB to check again is tracked for a full threshold before it is parked once more.
	 * currentStatusTimestamp cannot carry that: staleRequests.sql and requestsByLocalStatus.sql read it.
	 */
	public boolean hasBeenInCurrentStatusTooLong(PatronRequest patronRequest) {
		final var lastStatusChange = patronRequest.getCurrentStatusTimestamp();

		if (lastStatusChange == null) {
			return false;
		}

		final var parkBefore = Instant.now().minus(threshold);
		final var resumedAt = patronRequest.getTrackingResumedAt();

		return lastStatusChange.isBefore(parkBefore)
			&& (resumedAt == null || resumedAt.isBefore(parkBefore));
	}
}
