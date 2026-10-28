package org.olf.dcb.core.notifications;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class TrendMeasureTests {
	@Test
	void aWeekAlertsOnlyWhenItIsBothBusyEnoughAndWellAboveTheTrailingWeeks() {
		final var measure = TrendMeasure.UNFILLED_BORROWING;

		assertThat(measure.alerts(4, 1.0), is(true));
		assertThat(measure.alerts(2, 0.0), is(false));
		assertThat(measure.alerts(5, 3.0), is(false));
		assertThat(measure.alerts(6, 3.0), is(true));
	}

	@Test
	void everyMeasureCarriesAThreshold() {
		for (final var measure : TrendMeasure.values()) {
			assertThat(measure.name(), measure.alerts(Long.MAX_VALUE, 0.0), is(true));
			assertThat(measure.name(), measure.alerts(0, 0.0), is(false));
		}
	}

	@Test
	void aWeekStartsOnMondayAtMidnightUtc() {
		assertThat(StaffNotificationService.startOfWeek(Instant.parse("2026-10-28T18:00:00Z")),
			is(Instant.parse("2026-10-26T00:00:00Z")));
		assertThat(StaffNotificationService.startOfWeek(Instant.parse("2026-10-26T00:00:00Z")),
			is(Instant.parse("2026-10-26T00:00:00Z")));
	}
}
