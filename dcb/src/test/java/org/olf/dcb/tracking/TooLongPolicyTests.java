package org.olf.dcb.tracking;

import static java.util.UUID.randomUUID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.olf.dcb.tracking.TooLongPolicy.THRESHOLD_DAYS;
import static org.olf.dcb.tracking.TooLongPolicy.hasBeenInCurrentStatusTooLong;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.olf.dcb.core.model.PatronRequest;

class TooLongPolicyTests {
	@Test
	void aRequestThatChangedStatusRecentlyIsNotTooLong() {
		assertThat(hasBeenInCurrentStatusTooLong(request(daysAgo(1), null)), is(false));
	}

	@Test
	void aRequestStuckBeyondTheThresholdIsTooLong() {
		assertThat(hasBeenInCurrentStatusTooLong(request(daysAgo(THRESHOLD_DAYS + 1), null)), is(true));
	}

	@Test
	void aRequestResumedSinceTheThresholdIsTrackedAgain() {
		// The whole point of a manual update: the status has not changed, but somebody asked for it.
		assertThat(hasBeenInCurrentStatusTooLong(
			request(daysAgo(THRESHOLD_DAYS + 10), daysAgo(1))), is(false));
	}

	@Test
	void aRequestResumedLongerAgoThanTheThresholdIsTooLongAgain() {
		assertThat(hasBeenInCurrentStatusTooLong(
			request(daysAgo(THRESHOLD_DAYS * 2), daysAgo(THRESHOLD_DAYS + 1))), is(true));
	}

	@Test
	void aRequestThatHasNeverChangedStatusIsNotTooLong() {
		assertThat(hasBeenInCurrentStatusTooLong(request(null, null)), is(false));
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
