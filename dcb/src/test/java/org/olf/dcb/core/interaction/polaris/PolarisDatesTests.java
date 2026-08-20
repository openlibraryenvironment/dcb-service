package org.olf.dcb.core.interaction.polaris;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.nullValue;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

/**
 * The format here is not guesswork: /Date(1787240106747-0500)/ is the exact value a production
 * Polaris returned for AuthExpDate on 2026-08-19. Our mocks had claimed it was ISO-8601, which is
 * why the token TTL was originally not derived from it.
 */
class PolarisDatesTests {
	@Test
	void shouldParseTheFormatPolarisActuallyReturns() {
		assertThat(PolarisDates.parseMsJsonDate("/Date(1787240106747-0500)/"),
			is(Instant.parse("2026-08-20T15:35:06.747Z")));
	}

	@Test
	void shouldIgnoreTheOffsetBecauseTheValueIsAlreadyEpochMillis() {
		// Same instant, different display offsets - the offset must not shift the result.
		final var withNegative = PolarisDates.parseMsJsonDate("/Date(1787240106747-0500)/");
		final var withPositive = PolarisDates.parseMsJsonDate("/Date(1787240106747+0100)/");
		final var withNone = PolarisDates.parseMsJsonDate("/Date(1787240106747)/");

		assertThat(withPositive, is(withNegative));
		assertThat("A bare date with no offset should still parse", withNone, is(withNegative));
	}

	@Test
	void shouldBeCaseInsensitive() {
		assertThat(PolarisDates.parseMsJsonDate("/date(1787240106747+0000)/"),
			is(Instant.ofEpochMilli(1787240106747L)));
	}

	@Test
	void shouldReturnNullRatherThanThrowForAnythingElse() {
		// A bad date must never fail the request that carried it.
		assertThat(PolarisDates.parseMsJsonDate(null), is(nullValue()));
		assertThat(PolarisDates.parseMsJsonDate(""), is(nullValue()));
		assertThat(PolarisDates.parseMsJsonDate("not a date"), is(nullValue()));
		assertThat(PolarisDates.parseMsJsonDate("/Date()/"), is(nullValue()));
		// The format our mocks used to claim Polaris returned
		assertThat(PolarisDates.parseMsJsonDate("2023-09-18T16:40:04.652Z"), is(nullValue()));
	}

	@Test
	void shouldDeriveTtlLessASafetyMargin() {
		final var expiry = Instant.now().plus(Duration.ofHours(2));

		final var ttl = PolarisDates.ttlFromAuthExpDate("/Date(%d+0000)/".formatted(expiry.toEpochMilli()));

		// Two hours, less the 60s margin, allowing for clock movement during the test.
		assertThat(ttl, greaterThan(Duration.ofMinutes(118)));
		assertThat(ttl, lessThanOrEqualTo(Duration.ofMinutes(119)));
	}

	@Test
	void shouldNotDeriveATtlWhenExpiryCannotBeEstablished() {
		// Null means "cannot tell" - the caller falls back to its configured ceiling.
		assertThat(PolarisDates.ttlFromAuthExpDate(null), is(nullValue()));
		assertThat(PolarisDates.ttlFromAuthExpDate("rubbish"), is(nullValue()));
	}

	@Test
	void shouldNotDeriveATtlFromAnExpiryThatHasAlreadyPassed() {
		final var past = Instant.now().minus(Duration.ofHours(1));

		assertThat(PolarisDates.ttlFromAuthExpDate("/Date(%d+0000)/".formatted(past.toEpochMilli())),
			is(nullValue()));
	}

	@Test
	void shouldNotDeriveATtlInsideTheSafetyMargin() {
		// Expires in 30s, margin is 60s - too close to be worth caching.
		final var soon = Instant.now().plus(Duration.ofSeconds(30));

		assertThat(PolarisDates.ttlFromAuthExpDate("/Date(%d+0000)/".formatted(soon.toEpochMilli())),
			is(nullValue()));
	}
}
