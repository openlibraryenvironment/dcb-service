package org.olf.dcb.core.interaction.polaris;

import java.time.Duration;
import java.util.function.Supplier;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import jakarta.inject.Singleton;
import reactor.core.publisher.Mono;

/**
 * Each Polaris's item statuses and material types, read once an hour rather than by every client.
 * PolarisLmsClient is built per call, so a list held on the client was read again for every bib.
 */
@Singleton
class PolarisReferenceDataCache {
	enum Kind { ITEM_STATUSES, MATERIAL_TYPES }

	private record Key(String hostLmsCode, Kind kind) {}

	// A status added in Polaris is unknown to DCB for up to this long; its items read as UNKNOWN
	static final Duration TTL = Duration.ofHours(1);

	// Two kinds per Host LMS, for the scale constant of 500 libraries
	private final Cache<Key, Mono<?>> lists = Caffeine.newBuilder()
		.maximumSize(1000)
		.expireAfterWrite(TTL)
		.build();

	@SuppressWarnings("unchecked")
	<T> Mono<T> get(String hostLmsCode, Kind kind, Supplier<Mono<T>> fetch) {
		// Caffeine computes atomically, so concurrent callers share one read of Polaris
		return (Mono<T>) lists.get(new Key(hostLmsCode, kind),
			key -> fetch.get().cache(
				list -> TTL,
				// Never a failure or an empty answer: the next caller reads again
				error -> Duration.ZERO,
				() -> Duration.ZERO));
	}

	void invalidate(String hostLmsCode) {
		for (Kind kind : Kind.values()) {
			lists.invalidate(new Key(hostLmsCode, kind));
		}
	}
}
