package org.olf.dcb.tracking;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.olf.dcb.core.model.SupplierRequest;

class SupplierHoldReadFailuresPollIntervalTests {
	private static final Instant NOW = Instant.parse("2026-11-03T12:00:00Z");

	@Test
	void shouldKeepTheNormalIntervalWhileReadsSucceed() {
		assertThat(interval(Duration.ofSeconds(1), null), is(Duration.ofSeconds(1)));
	}

	@Test
	void shouldWaitFiveMinutesWhenReadsHaveJustStartedFailing() {
		assertThat(interval(Duration.ofSeconds(1), Duration.ofSeconds(10)), is(Duration.ofMinutes(5)));
	}

	@Test
	void shouldWaitAsLongAsReadsHaveBeenFailing() {
		assertThat(interval(Duration.ofMinutes(10), Duration.ofMinutes(30)), is(Duration.ofMinutes(30)));
	}

	@Test
	void shouldWaitNoMoreThanAnHour() {
		assertThat(interval(Duration.ofSeconds(1), Duration.ofHours(5)), is(Duration.ofHours(1)));
	}

	@Test
	void shouldKeepALongerNormalInterval() {
		assertThat(interval(Duration.ofHours(6), Duration.ofHours(5)), is(Duration.ofHours(6)));
	}

	private static Duration interval(Duration normal, Duration failingFor) {
		final var supplierRequest = SupplierRequest.builder()
			.localReadFailingSince(failingFor == null ? null : NOW.minus(failingFor))
			.build();

		return SupplierHoldReadFailures.pollInterval(normal, supplierRequest, NOW);
	}
}
