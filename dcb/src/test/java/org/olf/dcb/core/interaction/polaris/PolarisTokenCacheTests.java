package org.olf.dcb.core.interaction.polaris;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.olf.dcb.core.interaction.polaris.PolarisTokenCache.TokenKind.APPLICATION_SERVICES;
import static org.olf.dcb.core.interaction.polaris.PolarisTokenCache.TokenKind.PAPI_STAFF;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;

import reactor.core.publisher.Mono;

/**
 * Proves - rather than assumes - that the token cache actually stops us re-authenticating.
 * These are deliberately plain unit tests: the behaviour worth pinning down is when the
 * acquisition supplier is invoked, and that is far more legible without an HTTP round trip.
 */
class PolarisTokenCacheTests {
	private static final Duration TTL = Duration.ofMinutes(15);
	private static final String HOST_LMS_CODE = "a-polaris-system";

	@Test
	void shouldOnlyAuthenticateOnceWithinTheTimeToLive() {
		final var cache = new PolarisTokenCache();
		final var acquisitions = new AtomicInteger();

		final var first = fetchToken(cache, HOST_LMS_CODE, TTL, acquisitions);
		final var second = fetchToken(cache, HOST_LMS_CODE, TTL, acquisitions);

		assertThat(first, is("token-1"));
		assertThat("Second call should reuse the cached token", second, is("token-1"));
		assertThat("Polaris should only have been asked for a token once",
			acquisitions.get(), is(1));
	}

	@Test
	void shouldAuthenticateEveryTimeWhenCachingIsDisabled() {
		final var cache = new PolarisTokenCache();
		final var acquisitions = new AtomicInteger();

		// Contrast with the test above, so a passing result there cannot be a false positive:
		// a zero TTL has to bypass the cache completely.
		fetchToken(cache, HOST_LMS_CODE, Duration.ZERO, acquisitions);
		fetchToken(cache, HOST_LMS_CODE, Duration.ZERO, acquisitions);

		assertThat(acquisitions.get(), is(2));
	}

	@Test
	void shouldAuthenticateAgainAfterInvalidation() {
		final var cache = new PolarisTokenCache();
		final var acquisitions = new AtomicInteger();

		assertThat(fetchToken(cache, HOST_LMS_CODE, TTL, acquisitions), is("token-1"));

		// This is what a 401 from Polaris triggers - the token we held is no longer accepted.
		cache.invalidate(HOST_LMS_CODE);

		assertThat(fetchToken(cache, HOST_LMS_CODE, TTL, acquisitions), is("token-2"));
		assertThat(acquisitions.get(), is(2));
	}

	@Test
	void shouldNotShareATokenBetweenHostLmsOrTokenKinds() {
		final var cache = new PolarisTokenCache();
		final var acquisitions = new AtomicInteger();

		cache.get("first-polaris", APPLICATION_SERVICES, TTL, noExpiry(), counting(acquisitions)).block();
		cache.get("second-polaris", APPLICATION_SERVICES, TTL, noExpiry(), counting(acquisitions)).block();
		// PAPI and Application Services are separate credentials against separate APIs -
		// handing one's token to the other would fail authentication in a very confusing way.
		cache.get("first-polaris", PAPI_STAFF, TTL, noExpiry(), counting(acquisitions)).block();

		assertThat("Each Host LMS and token kind needs its own token",
			acquisitions.get(), is(3));
	}

	@Test
	void shouldNotCacheAFailedAuthentication() {
		final var cache = new PolarisTokenCache();
		final var attempts = new AtomicInteger();

		final Supplier<Mono<String>> flaky = () -> Mono.defer(() -> attempts.incrementAndGet() == 1
			? Mono.error(new RuntimeException("Polaris refused to authenticate"))
			: Mono.just("token-after-recovery"));

		assertThrows(RuntimeException.class,
			() -> cache.get(HOST_LMS_CODE, APPLICATION_SERVICES, TTL, noExpiry(), flaky).block());

		// Caching the failure would turn one bad moment into a TTL-long outage for this host.
		assertThat(cache.get(HOST_LMS_CODE, APPLICATION_SERVICES, TTL, noExpiry(), flaky).block(),
			is("token-after-recovery"));

		assertThat(attempts.get(), is(2));
	}

	private String fetchToken(PolarisTokenCache cache, String hostLmsCode,
		Duration ttl, AtomicInteger acquisitions) {

		return cache.get(hostLmsCode, APPLICATION_SERVICES, ttl, noExpiry(), counting(acquisitions)).block();
	}

	private Supplier<Mono<String>> counting(AtomicInteger acquisitions) {
		return () -> Mono.fromSupplier(() -> "token-" + acquisitions.incrementAndGet());
	}

	/** A token that says nothing about its own expiry, so the ceiling governs. */
	private Function<String, Duration> noExpiry() {
		return token -> null;
	}

	// --- TTL derivation ------------------------------------------------------------------
	// The cache trusts the token's stated expiry, but never beyond the configured ceiling.

	@Test
	void shouldUseTheDerivedTtlWhenItIsShorterThanTheCeiling() {
		assertThat(resolvedTtl(Duration.ofMinutes(5), Duration.ofHours(1)),
			is(Duration.ofMinutes(5)));
	}

	@Test
	void shouldCapTheDerivedTtlAtTheCeiling() {
		// Polaris grants 24 hours; we are not willing to hold a credential that long.
		assertThat(resolvedTtl(Duration.ofHours(24), Duration.ofHours(1)),
			is(Duration.ofHours(1)));
	}

	@Test
	void shouldFallBackToTheCeilingWhenExpiryCannotBeEstablished() {
		// null resolver result means "cannot tell", not "do not cache".
		assertThat(resolvedTtl(null, Duration.ofHours(1)), is(Duration.ofHours(1)));
	}

	@Test
	void shouldFallBackToTheCeilingWhenTheDerivedTtlIsNotPositive() {
		assertThat(resolvedTtl(Duration.ZERO, Duration.ofHours(1)), is(Duration.ofHours(1)));
		assertThat(resolvedTtl(Duration.ofMinutes(-5), Duration.ofHours(1)), is(Duration.ofHours(1)));
	}

	/** Calls the cache's own clamp, so these assertions cannot pass against a broken one. */
	private Duration resolvedTtl(Duration derived, Duration ceiling) {
		return PolarisTokenCache.clamp(token -> derived, "any-token", ceiling);
	}
}
