package org.olf.dcb.core.interaction;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

import java.time.Instant;

import org.junit.jupiter.api.Test;

/**
 * Two formats, both taken from Host LMS responses this codebase already parses elsewhere.
 * Anything else is unknown, and unknown is a first-class answer here rather than a failure.
 */
class DueDatesTests {

	@Test
	void readsTheIsoFormSierraAndKohaSend() {
		assertThat(DueDates.parse("2026-10-01T23:59:00Z"),
			is(Instant.parse("2026-10-01T23:59:00Z")));
	}

	@Test
	void readsPolarisApplicationServicesOwnFormat() {
		// Irregular internal spacing is Polaris' own, which is why the pattern tolerates it
		// rather than the input being trimmed.
		assertThat(DueDates.parse("Oct  1 2026  4:00PM"),
			is(Instant.parse("2026-10-01T16:00:00Z")));
	}

	@Test
	void anythingElseIsUnknownRatherThanAnException() {
		// This runs per item on a tracking sweep. A Host LMS that formats dates a third way
		// must cost a patron a due date, never cost the sweep a request.
		for (final var unreadable : new String[] { "", "  ", "next Tuesday", "01/10/2026" }) {
			assertThat(unreadable, DueDates.parse(unreadable), is(nullValue()));
		}
	}

	@Test
	void absentIsUnknownToo() {
		assertThat(DueDates.parse(null), is(nullValue()));
	}
}
