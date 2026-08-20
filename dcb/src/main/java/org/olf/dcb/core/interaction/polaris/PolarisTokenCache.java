package org.olf.dcb.core.interaction.polaris;

import java.time.Duration;
import java.util.function.Function;
import java.util.function.Supplier;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import jakarta.inject.Singleton;
import reactor.core.publisher.Mono;

/**
 * Caches Polaris staff authentication tokens per Host LMS.
 *
 * PolarisLmsClient is a @Prototype, so a new one - and with it a new auth filter - is built for
 * every HostLmsService.getClientFor(..) call. A token held in a filter field cannot survive that,
 * which is why both filters re-authenticated on every single request and put twice the necessary
 * load on Polaris. This cache sits outside that lifecycle.
 *
 * Keys are (Host LMS code, token kind): low cardinality and bounded, so this is safe to hold.
 */
@Singleton
class PolarisTokenCache {
	enum TokenKind { APPLICATION_SERVICES, PAPI_STAFF }

	private record Key(String hostLmsCode, TokenKind kind) {}

	// Memory backstop only - deliberately well clear of any sane ceiling, so it can never
	// truncate a TTL the caller actually intended. maximumSize is the real bound.
	private static final Duration ENTRY_BACKSTOP = Duration.ofHours(25);

	private final Cache<Key, Mono<?>> tokens = Caffeine.newBuilder()
		.maximumSize(500)
		.expireAfterWrite(ENTRY_BACKSTOP)
		.build();

	/**
	 * @param maxTtl ceiling on how long a token may be reused. Zero or negative bypasses the
	 *               cache entirely, which is how tests keep this singleton from leaking state.
	 * @param ttlResolver derives the TTL from the acquired token, normally from its stated
	 *                    expiry. Returning null means "cannot tell", and falls back to maxTtl.
	 * @param acquire performs the authentication request when there is nothing usable cached
	 */
	@SuppressWarnings("unchecked")
	<T> Mono<T> get(String hostLmsCode, TokenKind kind, Duration maxTtl,
		Function<T, Duration> ttlResolver, Supplier<Mono<T>> acquire) {

		if (maxTtl == null || maxTtl.isZero() || maxTtl.isNegative()) {
			return acquire.get();
		}

		// Caffeine computes atomically, so concurrent callers share a single in-flight
		// acquisition rather than each firing its own auth request at a struggling Polaris.
		return (Mono<T>) tokens.get(new Key(hostLmsCode, kind),
			key -> acquire.get()
				.cache(
					token -> clamp(ttlResolver, token, maxTtl),
					// Never cache a failure or an empty result - the next caller must retry.
					error -> Duration.ZERO,
					() -> Duration.ZERO));
	}

	/**
	 * Trust the server's stated expiry, but never beyond the configured ceiling - a token we
	 * hold is a credential, and a shorter window bounds the damage if one is revoked.
	 */
	// Package-private so PolarisTokenCacheTests can exercise the clamp directly rather than
	// re-implementing it.
	static <T> Duration clamp(Function<T, Duration> ttlResolver, T token, Duration maxTtl) {
		final var resolved = ttlResolver == null ? null : ttlResolver.apply(token);

		if (resolved == null || !resolved.isPositive()) {
			return maxTtl;
		}

		return resolved.compareTo(maxTtl) < 0 ? resolved : maxTtl;
	}

	/**
	 * Drops every cached token for a Host LMS, so the next request authenticates afresh.
	 * Called when Polaris rejects a token we believed was still good.
	 */
	void invalidate(String hostLmsCode) {
		for (TokenKind kind : TokenKind.values()) {
			tokens.invalidate(new Key(hostLmsCode, kind));
		}
	}
}
