package org.olf.dcb.core.interaction.koha;

import java.time.Duration;
import java.util.function.Supplier;

import org.olf.dcb.core.interaction.koha.dto.KohaAuthResponse;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import jakarta.inject.Singleton;
import reactor.core.publisher.Mono;

/**
 * One OAuth2 token per Koha Host LMS, shared by every client built for it.
 * <p>
 * A Koha client is built per HostLmsService.getClientFor call, so a token held on the client
 * was minted afresh for nearly every call, and Koha keeps each one as a row in
 * oauth_access_tokens until its nightly cleanup.
 */
@Singleton
class KohaTokenCache {
	// Koha refuses a token at its expiry; renewing a minute early keeps a request in flight
	// from carrying one that lapses on the way
	private static final Duration RENEW_BEFORE_EXPIRY = Duration.ofSeconds(60);

	// Memory backstop only: an entry renews its own token on expiry, maximumSize is the bound
	private static final Duration ENTRY_BACKSTOP = Duration.ofHours(25);

	private final Cache<String, Mono<String>> tokens = Caffeine.newBuilder()
		.maximumSize(500)
		.expireAfterWrite(ENTRY_BACKSTOP)
		.build();

	/**
	 * @param fetch the token request, made only when no unexpired token is held; concurrent
	 *              callers share one request in flight
	 */
	Mono<String> get(String hostLmsCode, Supplier<Mono<KohaAuthResponse>> fetch) {
		return tokens.get(hostLmsCode, code -> fetch.get()
			.cache(KohaTokenCache::reuseFor, error -> Duration.ZERO, () -> Duration.ZERO)
			.map(KohaAuthResponse::getAccessToken));
	}

	/** Called when Koha refuses a token it issued, so the next caller fetches a new one. */
	void invalidate(String hostLmsCode) {
		tokens.invalidate(hostLmsCode);
	}

	// No stated expiry, or one too short to renew ahead of: not reused
	static Duration reuseFor(KohaAuthResponse response) {
		if (response.getExpiresIn() == null) {
			return Duration.ZERO;
		}

		final var reuse = Duration.ofSeconds(response.getExpiresIn()).minus(RENEW_BEFORE_EXPIRY);

		return reuse.isPositive() ? reuse : Duration.ZERO;
	}
}
