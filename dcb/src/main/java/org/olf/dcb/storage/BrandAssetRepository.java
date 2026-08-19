package org.olf.dcb.storage;

import java.time.Instant;

import org.olf.dcb.core.model.StoredBrandAsset;
import org.reactivestreams.Publisher;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.async.annotation.SingleResult;

public interface BrandAssetRepository {

	@NonNull
	@SingleResult
	Publisher<? extends StoredBrandAsset> findById(@NonNull String assetKey);

	Publisher<Void> delete(@NonNull String assetKey);

	/**
	 * Store an asset, or do nothing if those exact bytes are already stored.
	 *
	 * <p>Content-addressed keys make re-uploading the same file idempotent, and two
	 * administrators uploading the same consortium logo minutes apart is an ordinary thing
	 * rather than a race worth reporting. The existing row is left alone: it is byte for
	 * byte the same asset, and its {@code date_created} is the one the sweep should use.
	 */
	@NonNull
	@SingleResult
	Publisher<Long> upsert(@NonNull String assetKey, @NonNull String contentType,
		@NonNull byte[] bytes, @NonNull Instant now);

	/**
	 * Delete stored assets that no brand field refers to and that are older than the
	 * cutoff.
	 *
	 * <p>The cutoff is a grace period, and it is not optional. An administrator uploads,
	 * receives a URL, and saves it against a brand field with a SEPARATE mutation — so
	 * between those two calls an asset is legitimately unreferenced. Sweeping without a
	 * window would delete the image somebody is part-way through choosing.
	 *
	 * <p>This is the query object storage could not do cheaply, and the reason
	 * {@code BrandAssetStore}'s original design ruled a sweep out: knowing which keys are
	 * still referenced by any brand field on any row is one statement when the assets and
	 * the brand columns live in the same database.
	 *
	 * @return rows deleted
	 */
	@NonNull
	@SingleResult
	Publisher<Long> deleteUnreferencedBefore(@NonNull String pathPrefix, @NonNull Instant cutoff);
}
