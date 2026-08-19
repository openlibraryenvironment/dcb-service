package org.olf.dcb.storage.postgres;

import java.time.Instant;

import org.olf.dcb.core.model.StoredBrandAsset;
import org.olf.dcb.storage.BrandAssetRepository;
import org.reactivestreams.Publisher;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.r2dbc.annotation.R2dbcRepository;
import io.micronaut.data.repository.reactive.ReactiveStreamsCrudRepository;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

@Singleton
@R2dbcRepository(dialect = Dialect.POSTGRES)
@Transactional
public interface PostgresBrandAssetRepository
	extends ReactiveStreamsCrudRepository<StoredBrandAsset, String>, BrandAssetRepository {

	@Override
	@NonNull
	@SingleResult
	Publisher<StoredBrandAsset> findById(@NonNull String assetKey);

	@Override
	@NonNull
	@SingleResult
	@Query(value = """
		INSERT INTO brand_asset (asset_key, content_type, bytes, date_created)
		VALUES (:assetKey, :contentType, :bytes, :now)
		ON CONFLICT (asset_key) DO NOTHING
		""", nativeQuery = true)
	Publisher<Long> upsert(@NonNull String assetKey, @NonNull String contentType,
		@NonNull byte[] bytes, @NonNull Instant now);

	/**
	 * Four columns can hold an uploaded asset, and every one of them has to be named here.
	 * A fifth added without touching this query would have its images swept out from under
	 * it — which is why {@code BrandAssetSweepTests} asserts one case per column rather
	 * than one case overall.
	 */
	@Override
	@NonNull
	@SingleResult
	@Query(value = """
		DELETE FROM brand_asset a
		WHERE a.date_created < :cutoff
			AND NOT EXISTS (
				SELECT 1 FROM consortium c
				WHERE :pathPrefix || a.asset_key IN (
					c.brand_logo_url, c.brand_header_icon_url, c.brand_background_image_url))
			AND NOT EXISTS (
				SELECT 1 FROM library l
				WHERE :pathPrefix || a.asset_key = l.brand_logo_url)
		""", nativeQuery = true)
	Publisher<Long> deleteUnreferencedBefore(@NonNull String pathPrefix, @NonNull Instant cutoff);
}
