package org.olf.dcb.core.branding;

import java.time.Clock;
import java.time.Duration;

import org.olf.dcb.storage.BrandAssetRepository;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * Brand images stored in Postgres (R-17b).
 *
 * <h2>Why the database and not object storage</h2>
 *
 * Requiring an S3-API bucket makes uploading conditional on infrastructure a deployment may
 * not have and a developer almost certainly does not: it needs a container, three
 * {@code AWS_*} exports, an endpoint override and path-style addressing before anybody can
 * exercise the route at all. Postgres is already there — {@code DcbTestContainerContextBuilder}
 * stands one up for every {@code @DcbTest} — so the upload path is covered locally and in
 * CI rather than only once deployed.
 *
 * <p>The size objection does not survive the numbers. Four columns can reference an
 * uploaded asset, so at 500 libraries the referenced set is 503 images, each capped at 2 MB
 * before storage. That is a bounded fraction of a database holding 20 million bibliographic
 * records. What is unbounded is uploads nobody saves, which is what {@link BrandAssetSweep}
 * is for.
 *
 * <p>Object storage is a reasonable option for estates that already run it, and it will
 * come back as one. It is not the right thing to require of everybody.
 *
 * <h2>Reads are cached, because the read path is anonymous</h2>
 *
 * Uploads are rare. Reads are not: the serve route is anonymous and hit on the first paint
 * of every patron page whose browser cache is cold. Content-addressed keys make this the
 * easy case — an entry can never be wrong, only absent, so there is no invalidation to get
 * right. Bounded by BYTES rather than entry count, because one 2 MB background and a
 * hundred small marks must not be charged the same.
 *
 * <p>The cache lives here rather than in a decorator because there is currently one
 * implementation. When object storage returns there will be two, both wanting exactly this,
 * and that is the moment to lift it out — not before.
 */
@Singleton
@Requires(property = "dcb.branding.assets.store", value = "database", defaultValue = "database")
@Slf4j
public class DatabaseBrandAssetStore implements BrandAssetStore {

	/** A ceiling on what served brand images may cost the heap. */
	static final long MAX_CACHE_BYTES = 64L * 1024 * 1024;

	private static final Duration CACHE_TTL = Duration.ofHours(6);

	private final BrandAssetRepository repository;
	private final Clock clock;

	private final Cache<String, BrandAsset> served = createServedCache();

	static Cache<String, BrandAsset> createServedCache() {
		return Caffeine.newBuilder()
			.maximumWeight(MAX_CACHE_BYTES)
			.weigher((String key, BrandAsset asset) -> asset.size())
			.expireAfterAccess(CACHE_TTL)
			.build();
	}

	public DatabaseBrandAssetStore(BrandAssetRepository repository) {
		this(repository, Clock.systemUTC());
	}

	DatabaseBrandAssetStore(BrandAssetRepository repository, Clock clock) {
		this.repository = repository;
		this.clock = clock;
	}

	@Override
	public Mono<String> put(BrandAsset asset) {
		final var key = asset.key();

		return Mono.from(repository.upsert(key, asset.contentType(), asset.bytes(),
				clock.instant()))
			.doOnNext(written -> log.info("Stored brand asset {} ({} bytes, {}){}",
				key, asset.size(), asset.contentType(),
				written == 0 ? " - already present" : ""))
			.thenReturn(key);
	}

	@Override
	public Mono<BrandAsset> get(String key) {
		final var cached = served.getIfPresent(key);

		if (cached != null) {
			return Mono.just(cached);
		}

		return Mono.from(repository.findById(key))
			.map(stored -> new BrandAsset(stored.getContentType(), stored.getBytes()))
			// Only a hit is cached. Caching "there is no such key" would leave an asset
			// invisible for the TTL if somebody asked for it a second before it was
			// uploaded, and an absent row is already the cheap answer.
			.doOnNext(asset -> served.put(key, asset));
	}

	@Override
	public Mono<Void> delete(String key) {
		// Evicted first, and unconditionally. Dropping an entry we could have kept is the
		// cheap mistake; serving one that no longer exists is not.
		served.invalidate(key);

		return Mono.from(repository.delete(key))
			.doOnError(e -> log.warn("Could not remove replaced brand asset {}: {}", key, e.getMessage()))
			// Best effort, by design. An administrator whose new logo is live must not be
			// shown an error because the old row survived.
			.onErrorResume(e -> Mono.empty())
			.then();
	}
}
