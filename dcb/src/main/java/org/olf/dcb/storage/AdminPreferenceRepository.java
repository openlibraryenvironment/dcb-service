package org.olf.dcb.storage;

import java.time.Instant;
import java.util.Collection;
import java.util.UUID;

import org.olf.dcb.core.model.AdminPreference;
import org.reactivestreams.Publisher;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.async.annotation.SingleResult;

/** Preferences and watched requests, every statement scoped by the caller's key. */
public interface AdminPreferenceRepository {
	@NonNull
	@SingleResult
	Publisher<? extends AdminPreference> findById(@NonNull String adminKey);

	@NonNull
	@SingleResult
	Publisher<Long> deleteByAdminKey(@NonNull String adminKey);

	@NonNull
	@SingleResult
	Publisher<Long> upsertDisplay(@NonNull String adminKey, @Nullable String themeMode, @Nullable String textSize,
		@Nullable String density, @Nullable String fontName, @Nullable String motion, @Nullable String language,
		@NonNull Instant now);

	/** Replaces one app's slice of app_state and leaves the other's alone. */
	@NonNull
	@SingleResult
	Publisher<Long> upsertAppSlice(@NonNull String adminKey, @NonNull String app, @NonNull String sliceJson,
		@NonNull Instant now);

	@NonNull
	@SingleResult
	Publisher<Long> touch(@NonNull String adminKey, @NonNull Instant now, @NonNull Instant staleBefore);

	@NonNull
	@SingleResult
	Publisher<Long> ensureExists(@NonNull String adminKey, @NonNull Instant now);

	@NonNull
	@SingleResult
	Publisher<Long> deleteNotSeenSince(@NonNull Instant cutoff, int limit);

	@NonNull
	@SingleResult
	Publisher<Long> watch(@NonNull String adminKey, @NonNull UUID patronRequestId, @NonNull Instant now);

	@NonNull
	@SingleResult
	Publisher<Long> unwatch(@NonNull String adminKey, @NonNull UUID patronRequestId);

	@NonNull
	@SingleResult
	Publisher<Long> countWatched(@NonNull String adminKey);

	@NonNull
	@SingleResult
	Publisher<Long> countWatchedRequest(@NonNull String adminKey, @NonNull UUID patronRequestId);

	@NonNull
	Publisher<WatchedRow> findWatched(@NonNull String adminKey, int limit, long offset);

	@NonNull
	@SingleResult
	Publisher<Long> markViewed(@NonNull String adminKey, @NonNull UUID patronRequestId, @NonNull Instant now);

	/** Watches whose request has gone, or ended before the cutoff; at most {@code limit} a call. */
	@NonNull
	@SingleResult
	Publisher<Long> deleteWatchesGoneOrEndedBefore(@NonNull Collection<String> terminalStatuses,
		@NonNull Instant endedBefore, int limit);

	@Introspected
	record WatchedRow(UUID patron_request_id, Instant date_created, Instant date_last_viewed) {
	}
}
