package org.olf.dcb.storage.postgres;

import java.time.Instant;
import java.util.Collection;
import java.util.UUID;

import org.olf.dcb.core.model.AdminPreference;
import org.olf.dcb.storage.AdminPreferenceRepository;
import org.reactivestreams.Publisher;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
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
public interface PostgresAdminPreferenceRepository
	extends ReactiveStreamsCrudRepository<AdminPreference, String>, AdminPreferenceRepository {

	@Override
	@NonNull
	@SingleResult
	Publisher<AdminPreference> findById(@NonNull String adminKey);

	@Override
	@NonNull
	@SingleResult
	@Query(value = "DELETE FROM admin_preference WHERE admin_key = :adminKey", nativeQuery = true)
	Publisher<Long> deleteByAdminKey(@NonNull String adminKey);

	@Override
	@NonNull
	@SingleResult
	@Query(value = """
		INSERT INTO admin_preference (admin_key, theme_mode, text_size, density, font_name, motion, language,
			app_state, date_created, date_updated, date_last_seen)
		VALUES (:adminKey, :themeMode, :textSize, :density, :fontName, :motion, :language,
			CAST('{}' AS jsonb), :now, :now, :now)
		ON CONFLICT (admin_key) DO UPDATE SET
			theme_mode = EXCLUDED.theme_mode,
			text_size = EXCLUDED.text_size,
			density = EXCLUDED.density,
			font_name = EXCLUDED.font_name,
			motion = EXCLUDED.motion,
			language = EXCLUDED.language,
			date_updated = :now,
			date_last_seen = :now
		""", nativeQuery = true)
	Publisher<Long> upsertDisplay(@NonNull String adminKey, @Nullable String themeMode, @Nullable String textSize,
		@Nullable String density, @Nullable String fontName, @Nullable String motion, @Nullable String language,
		@NonNull Instant now);

	@Override
	@NonNull
	@SingleResult
	@Query(value = """
		INSERT INTO admin_preference (admin_key, app_state, date_created, date_updated, date_last_seen)
		VALUES (:adminKey, jsonb_build_object(CAST(:app AS text), CAST(:sliceJson AS jsonb)), :now, :now, :now)
		ON CONFLICT (admin_key) DO UPDATE SET
			app_state = jsonb_set(admin_preference.app_state, ARRAY[CAST(:app AS text)], CAST(:sliceJson AS jsonb), true),
			date_updated = :now,
			date_last_seen = :now
		""", nativeQuery = true)
	Publisher<Long> upsertAppSlice(@NonNull String adminKey, @NonNull String app, @NonNull String sliceJson,
		@NonNull Instant now);

	@Override
	@NonNull
	@SingleResult
	@Query(value = """
		UPDATE admin_preference SET date_last_seen = :now
		WHERE admin_key = :adminKey AND date_last_seen < :staleBefore
		""", nativeQuery = true)
	Publisher<Long> touch(@NonNull String adminKey, @NonNull Instant now, @NonNull Instant staleBefore);

	@Override
	@NonNull
	@SingleResult
	@Query(value = """
		INSERT INTO admin_preference (admin_key, app_state, date_created, date_updated, date_last_seen)
		VALUES (:adminKey, CAST('{}' AS jsonb), :now, :now, :now)
		ON CONFLICT (admin_key) DO UPDATE SET date_last_seen = :now
		""", nativeQuery = true)
	Publisher<Long> ensureExists(@NonNull String adminKey, @NonNull Instant now);

	@Override
	@NonNull
	@SingleResult
	@Query(value = """
		DELETE FROM admin_preference WHERE admin_key IN (
			SELECT admin_key FROM admin_preference WHERE date_last_seen < :cutoff LIMIT :limit)
		""", nativeQuery = true)
	Publisher<Long> deleteNotSeenSince(@NonNull Instant cutoff, int limit);

	@Override
	@NonNull
	@SingleResult
	@Query(value = """
		INSERT INTO admin_watched_request (admin_key, patron_request_id, date_created, date_last_viewed)
		VALUES (:adminKey, :patronRequestId, :now, :now)
		ON CONFLICT (admin_key, patron_request_id) DO NOTHING
		""", nativeQuery = true)
	Publisher<Long> watch(@NonNull String adminKey, @NonNull UUID patronRequestId, @NonNull Instant now);

	@Override
	@NonNull
	@SingleResult
	@Query(value = """
		DELETE FROM admin_watched_request WHERE admin_key = :adminKey AND patron_request_id = :patronRequestId
		""", nativeQuery = true)
	Publisher<Long> unwatch(@NonNull String adminKey, @NonNull UUID patronRequestId);

	@Override
	@NonNull
	@SingleResult
	@Query(value = "SELECT count(*) FROM admin_watched_request WHERE admin_key = :adminKey", nativeQuery = true)
	Publisher<Long> countWatched(@NonNull String adminKey);

	@Override
	@NonNull
	@SingleResult
	@Query(value = """
		SELECT count(*) FROM admin_watched_request WHERE admin_key = :adminKey AND patron_request_id = :patronRequestId
		""", nativeQuery = true)
	Publisher<Long> countWatchedRequest(@NonNull String adminKey, @NonNull UUID patronRequestId);

	@Override
	@NonNull
	@Query(value = """
		SELECT patron_request_id, date_created, date_last_viewed FROM admin_watched_request
		WHERE admin_key = :adminKey
		ORDER BY date_created DESC, patron_request_id
		LIMIT :limit OFFSET :offset
		""", nativeQuery = true)
	Publisher<WatchedRow> findWatched(@NonNull String adminKey, int limit, long offset);

	@Override
	@NonNull
	@SingleResult
	@Query(value = """
		UPDATE admin_watched_request SET date_last_viewed = :now
		WHERE admin_key = :adminKey AND patron_request_id = :patronRequestId
		""", nativeQuery = true)
	Publisher<Long> markViewed(@NonNull String adminKey, @NonNull UUID patronRequestId, @NonNull Instant now);

	@Override
	@NonNull
	@SingleResult
	@Query(value = """
		DELETE FROM admin_watched_request WHERE (admin_key, patron_request_id) IN (
			SELECT w.admin_key, w.patron_request_id FROM admin_watched_request w
			WHERE NOT EXISTS (SELECT 1 FROM patron_request pr WHERE pr.id = w.patron_request_id)
			   OR EXISTS (SELECT 1 FROM patron_request pr WHERE pr.id = w.patron_request_id
			     AND pr.status_code IN (:terminalStatuses) AND pr.current_status_timestamp < :endedBefore)
			LIMIT :limit)
		""", nativeQuery = true)
	Publisher<Long> deleteWatchesGoneOrEndedBefore(@NonNull Collection<String> terminalStatuses,
		@NonNull Instant endedBefore, int limit);
}
