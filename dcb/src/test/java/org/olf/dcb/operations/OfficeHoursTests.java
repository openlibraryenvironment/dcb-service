package org.olf.dcb.operations;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import java.time.OffsetTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

class OfficeHoursTests {
	@Test
	void shouldTreatAWindowAcrossMidnightAsOneWindow() {
		final var overnight = new OfficeHours("22:00", "06:00");

		assertThat(overnight.isInsideHours(utc(23, 0)), is(true));
		assertThat(overnight.isInsideHours(utc(3, 0)), is(true));
		assertThat(overnight.isInsideHours(utc(22, 0)), is(true));
		assertThat(overnight.isInsideHours(utc(6, 0)), is(true));
		assertThat(overnight.isInsideHours(utc(12, 0)), is(false));
		assertThat(overnight.isInsideHours(utc(21, 59)), is(false));
	}

	@Test
	void shouldKeepASameDayWindowAsItWas() {
		final var working = new OfficeHours("08:00", "18:00");

		assertThat(working.isInsideHours(utc(8, 0)), is(true));
		assertThat(working.isInsideHours(utc(12, 0)), is(true));
		assertThat(working.isInsideHours(utc(18, 0)), is(true));
		assertThat(working.isInsideHours(utc(7, 59)), is(false));
		assertThat(working.isInsideHours(utc(23, 0)), is(false));
	}

	@Test
	void shouldCompareInUtcWhateverTheCallersOffset() {
		final var overnight = new OfficeHours("22:00", "06:00");

		assertThat(overnight.isInsideHours(OffsetTime.of(1, 0, 0, 0, ZoneOffset.ofHours(2))), is(true));
	}

	@Test
	void shouldBeOutsideHoursWhenNeitherEndIsSet() {
		assertThat(new OfficeHours(null, null).isInsideHours(utc(12, 0)), is(false));
	}

	private static OffsetTime utc(int hour, int minute) {
		return OffsetTime.of(hour, minute, 0, 0, ZoneOffset.UTC);
	}
}
