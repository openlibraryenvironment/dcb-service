package org.olf.dcb.storage;

import java.time.Instant;
import java.util.Collection;
import java.util.UUID;

import org.olf.dcb.core.model.Announcement;
import org.reactivestreams.Publisher;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.data.annotation.Query;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public interface AnnouncementRepository {

	@NonNull
	@SingleResult
	Publisher<? extends Announcement> save(@Valid @NotNull @NonNull Announcement announcement);

	@NonNull
	@SingleResult
	Publisher<? extends Announcement> update(@Valid @NotNull @NonNull Announcement announcement);

	@NonNull
	@SingleResult
	Publisher<Announcement> findById(@NonNull UUID id);

	/**
	 * The newest announcement live at one scope right now, or only the newest urgent one.
	 *
	 * The window is compared in SQL rather than filtered in memory: an expired announcement
	 * that reaches the application at all is one a bug can render.
	 *
	 * One row, on the index on (scope_type, scope_id, starts_at, expires_at).
	 */
	@Query(value = """
		SELECT * FROM announcement
		WHERE scope_type = :scopeType AND scope_id = :scopeId
		  AND starts_at <= :now AND expires_at > :now
		  AND (urgent OR NOT :urgentOnly)
		ORDER BY starts_at DESC
		LIMIT 1
		""", nativeQuery = true)
	@SingleResult
	Publisher<Announcement> findNewestLive(@NonNull String scopeType, @NonNull UUID scopeId,
		@NonNull Instant now, boolean urgentOnly);

	/** The newest live announcement across several scopes of one type: a library's groups. */
	@Query(value = """
		SELECT * FROM announcement
		WHERE scope_type = :scopeType AND scope_id IN (:scopeIds)
		  AND starts_at <= :now AND expires_at > :now
		  AND (urgent OR NOT :urgentOnly)
		ORDER BY starts_at DESC
		LIMIT 1
		""", nativeQuery = true)
	@SingleResult
	Publisher<Announcement> findNewestLiveIn(@NonNull String scopeType, @NonNull Collection<UUID> scopeIds,
		@NonNull Instant now, boolean urgentOnly);

	/**
	 * The admin list for one scope, newest first. Expired notices are never deleted, so it is
	 * capped at the 100 rows one screen shows.
	 */
	@Query(value = """
		SELECT * FROM announcement
		WHERE scope_type = :scopeType AND scope_id = :scopeId
		ORDER BY starts_at DESC
		LIMIT 100
		""", nativeQuery = true)
	Publisher<Announcement> findForAdministration(@NonNull String scopeType, @NonNull UUID scopeId);

	@NonNull
	Publisher<? extends Announcement> queryAll();

	@NonNull
	Publisher<Void> delete(@NonNull UUID id);
}
