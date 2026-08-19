package org.olf.dcb.core.branding;

import java.time.Clock;

import org.olf.dcb.storage.BrandAssetRepository;

import io.micronaut.context.annotation.Requires;
import io.micronaut.scheduling.annotation.Scheduled;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import services.k_int.micronaut.scheduling.processor.AppTask;

/**
 * Removes uploaded images that no brand field refers to (R-17b).
 *
 * <h2>Why this has to exist</h2>
 *
 * {@link BrandAssetCleanup} handles the other half — an asset that was referenced and then
 * replaced — and it is driven by the field changing, so it only ever sees an asset that was
 * once in use. Uploads are not bounded by the four columns that can reference one: an
 * administrator uploads, receives a URL, and saves it with a SEPARATE mutation. An upload
 * that is never saved is orphaned the moment it lands, and nothing else would ever notice.
 *
 * <p>Without this, an authenticated administrator can insert unbounded two-megabyte rows by
 * uploading repeatedly and saving nothing. In a bucket that is a bucket growing. Here it is
 * the transactional database and every backup of it, which is worse — so the store that
 * carries the risk is the one that has to answer for it.
 *
 * <h2>Why it is affordable here and was not in object storage</h2>
 *
 * The expensive part of a sweep is knowing which keys are still referenced by any brand
 * field on any row. Against a bucket that means enumerating objects and cross-checking them
 * against the database. When the assets and the brand columns are in the same database it
 * is one statement — see {@code PostgresBrandAssetRepository.deleteUnreferencedBefore}.
 *
 * <h2>The grace period is load-bearing</h2>
 *
 * Between the upload and the mutation that saves the URL, an asset is legitimately
 * unreferenced. A sweep with no window would delete the image an administrator is part-way
 * through choosing, and they would be told nothing — the form would simply show a broken
 * image after they saved. A day is generous for a form submission and short enough that
 * abandoned uploads do not accumulate.
 */
@Singleton
@Requires(beans = BrandAssetStore.class)
@Slf4j
public class BrandAssetSweep {

	private final BrandAssetRepository repository;
	private final BrandAssetProperties properties;
	private final Clock clock;

	public BrandAssetSweep(BrandAssetRepository repository, BrandAssetProperties properties) {
		this(repository, properties, Clock.systemUTC());
	}

	BrandAssetSweep(BrandAssetRepository repository, BrandAssetProperties properties, Clock clock) {
		this.repository = repository;
		this.properties = properties;
		this.clock = clock;
	}

	@AppTask
	@Scheduled(initialDelay = "15m", fixedDelay = "${dcb.branding.assets.sweep-interval:24h}")
	public void scheduledSweep() {
		sweep()
			.doOnSuccess(removed -> {
				if (removed > 0) {
					log.info("Brand asset sweep: removed {} unreferenced image(s)", removed);
				}
			})
			.doOnError(error -> log.error("Brand asset sweep failed", error))
			.subscribe();
	}

	/**
	 * One pass.
	 *
	 * @return how many unreferenced assets were removed
	 */
	public Mono<Long> sweep() {
		final var cutoff = clock.instant().minus(properties.getOrphanGracePeriod());

		return Mono.from(repository.deleteUnreferencedBefore(
				properties.getPublicPathPrefix(), cutoff))
			.defaultIfEmpty(0L);
	}
}
