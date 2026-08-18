package org.olf.dcb.core.branding;

import java.util.Optional;

import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * The orphan policy, which is delete-on-replace and is stated rather than assumed
 * (R-17b).
 *
 * <h2>Why there has to be one</h2>
 *
 * Keys are content-addressed, so replacing an asset never overwrites the old object — it
 * writes a second one and leaves the first. Without a policy the bucket grows once per
 * edit, forever. An unbounded bucket is the same defect as an unbounded table; it is just
 * slower to notice because nothing queries it.
 *
 * <h2>Why delete-on-replace and not a sweep</h2>
 *
 * A sweep needs a scheduler, a way to know which keys are still referenced by any brand
 * field on any row, and somebody to notice when it stops running. Delete-on-replace needs
 * the previous value of the column, which the update already has in its hand. The moment
 * an asset stops being referenced is exactly the moment we are told about it, so there is
 * nothing to discover later.
 *
 * <h2>Two things it deliberately does not do</h2>
 *
 * It never touches an absolute URL. A consortium's CDN is not ours to delete from, and
 * the same URL may be in use by things we know nothing about.
 *
 * It never fails an update. An administrator whose new logo is live must not be shown an
 * error because a stale object survived — the cost of that leak is one image, and the
 * cost of the alternative is an edit that looks broken.
 */
@Singleton
@Slf4j
public class BrandAssetCleanup {

	private final Optional<BrandAssetStore> store;
	private final String assetPathPrefix;

	public BrandAssetCleanup(Optional<BrandAssetStore> store, BrandAssetProperties properties) {
		this.store = store;
		this.assetPathPrefix = properties.getPublicPathPrefix();
	}

	/**
	 * Remove any asset this deployment stored that the given edit has stopped
	 * referencing.
	 *
	 * Pairs are (previous, current). A pair whose previous value is unchanged, absent,
	 * external, or still in use as the new value of another field is left alone — the
	 * last of those matters, because a consortium that points its header icon at the
	 * image its logo already uses must not have the object deleted out from under one of
	 * them.
	 */
	public Mono<Void> removeReplaced(java.util.List<Change> changes) {
		if (store.isEmpty() || changes.isEmpty()) {
			return Mono.empty();
		}

		final var stillReferenced = changes.stream()
			.map(Change::current)
			.filter(this::isOurs)
			.map(this::keyOf)
			.collect(java.util.stream.Collectors.toSet());

		final var toDelete = changes.stream()
			.map(Change::previous)
			.filter(this::isOurs)
			.map(this::keyOf)
			.filter(key -> !stillReferenced.contains(key))
			.distinct()
			.toList();

		if (toDelete.isEmpty()) {
			return Mono.empty();
		}

		log.debug("Removing {} replaced brand asset(s)", toDelete.size());

		// One at a time. This is at most a handful of objects on an administrator's
		// occasional edit, and a concurrency argument for something that cannot exceed
		// the number of brand fields on one row would be decoration.
		return Flux.fromIterable(toDelete)
			.concatMap(key -> store.get().delete(key))
			.then();
	}

	private boolean isOurs(String url) {
		return url != null && url.startsWith(assetPathPrefix);
	}

	private String keyOf(String url) {
		return url.substring(assetPathPrefix.length());
	}

	/** One brand field's before and after. Either side may be null. */
	public record Change(String previous, String current) {
	}
}
