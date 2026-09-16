package org.olf.dcb.tracking;

import static java.util.UUID.randomUUID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.olf.dcb.core.model.PatronRequest;

/**
 * No database and no application context: the policy is a decision about two timestamps, and
 * the threshold is passed in, so every case is exercised at more than one configured value.
 */
class TooLongPolicyTests {
	private static final Duration DEFAULT = Duration.ofDays(56);
	private static final Duration SHORTER = Duration.ofDays(7);

	@Test
	void aRequestThatChangedStatusRecentlyIsNotTooLong() {
		assertThat(policy(DEFAULT).hasBeenInCurrentStatusTooLong(request(daysAgo(1), null)), is(false));
		assertThat(policy(SHORTER).hasBeenInCurrentStatusTooLong(request(daysAgo(1), null)), is(false));
	}

	@Test
	void aRequestStuckBeyondTheThresholdIsTooLong() {
		assertThat(policy(DEFAULT).hasBeenInCurrentStatusTooLong(request(daysAgo(57), null)), is(true));
		assertThat(policy(SHORTER).hasBeenInCurrentStatusTooLong(request(daysAgo(8), null)), is(true));
	}

	@Test
	@DisplayName("The threshold is the configured one, not a compiled-in 56 days")
	void theConfiguredThresholdIsWhatDecides() {
		// 30 days stuck: too long on a seven day threshold, not on the default. A hardcoded
		// constant cannot satisfy both of these.
		final var stuckThirtyDays = request(daysAgo(30), null);

		assertThat(policy(SHORTER).hasBeenInCurrentStatusTooLong(stuckThirtyDays), is(true));
		assertThat(policy(DEFAULT).hasBeenInCurrentStatusTooLong(stuckThirtyDays), is(false));
	}

	@Test
	void aRequestResumedSinceTheThresholdIsTrackedAgain() {
		// The whole point of a manual update: the status has not changed, but somebody asked for it.
		assertThat(policy(DEFAULT).hasBeenInCurrentStatusTooLong(request(daysAgo(66), daysAgo(1))),
			is(false));
	}

	@Test
	void aRequestResumedLongerAgoThanTheThresholdIsTooLongAgain() {
		assertThat(policy(DEFAULT).hasBeenInCurrentStatusTooLong(request(daysAgo(112), daysAgo(57))),
			is(true));
	}

	@Test
	void aRequestThatHasNeverChangedStatusIsNotTooLong() {
		assertThat(policy(DEFAULT).hasBeenInCurrentStatusTooLong(request(null, null)), is(false));
	}

	@Test
	void theThresholdIsReadableForTheAuditEntry() {
		assertThat(policy(SHORTER).threshold(), is(SHORTER));
	}

	private static TooLongPolicy policy(Duration threshold) {
		return new TooLongPolicy(threshold);
	}

	private static PatronRequest request(Instant currentStatusTimestamp, Instant trackingResumedAt) {
		return PatronRequest.builder()
			.id(randomUUID())
			.currentStatusTimestamp(currentStatusTimestamp)
			.trackingResumedAt(trackingResumedAt)
			.build();
	}

	private static Instant daysAgo(int days) {
		return Instant.now().minus(Duration.ofDays(days));
	}
}
