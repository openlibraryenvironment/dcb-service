package org.olf.dcb.core.branding;

import reactor.core.publisher.Mono;

/**
 * Where a consortium's or a library's uploaded brand image actually lives (R-17b).
 *
 * <h2>Why an interface, when there is one implementation</h2>
 *
 * The same seam {@code HostLmsClient} and the ingest source adapters already use. It is
 * here for two concrete reasons rather than for symmetry:
 *
 * <ol>
 *   <li>The tests need a store that does not require a bucket. An in-memory one is four
 *       lines behind this interface and a mocked S3 client is not.</li>
 *   <li>Proxying the upload rather than presigning a direct PUT is a decision that could
 *       be revisited at a volume we do not have (§R-7.4). If it ever is, a presigned PUT
 *       is a different implementation of this interface and nothing above it changes.</li>
 * </ol>
 *
 * <h2>Content-addressed keys</h2>
 *
 * {@link #put} names the object after the SHA-256 of its bytes. That is what makes the
 * served URL immutable and therefore cacheable forever: a replaced logo is a different
 * URL, so no cache anywhere has to be told about the change, and a consortium that
 * re-uploads the identical file costs one PUT and no new object.
 *
 * <h2>Orphans, and the policy is delete-on-replace</h2>
 *
 * A content-addressed key guarantees that replacing an asset leaves the old object
 * behind. {@link BrandAssetService} deletes the previous object when a brand field is
 * pointed somewhere else, and that is the whole policy: no sweep, no lifecycle rule to
 * configure, nothing that only runs if somebody remembered to schedule it.
 *
 * It is deliberately best-effort. A failed delete logs and does not fail the update — an
 * administrator whose new logo is live must not be shown an error because a stale object
 * survived, and the cost of the leak is one image. An unbounded bucket would be the same
 * defect as an unbounded table; a bucket that leaks an object when a delete call fails is
 * not that.
 */
public interface BrandAssetStore {

	/**
	 * Store the asset and return its key, which is content-addressed and includes an
	 * extension so the object is recognisable in a bucket listing.
	 */
	Mono<String> put(BrandAsset asset);

	/** Empty when there is no such key — a 404, not an error. */
	Mono<BrandAsset> get(String key);

	/** Best-effort. Completes empty whether or not anything was there to remove. */
	Mono<Void> delete(String key);
}
