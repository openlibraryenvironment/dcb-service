package org.olf.dcb.storage;

import java.util.UUID;

import org.olf.dcb.core.model.AdminSavedView;
import org.reactivestreams.Publisher;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.async.annotation.SingleResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * Saved grid views.
 *
 * <p>Every read is filtered by {@code adminKey} as well as by id. That is not
 * belt-and-braces: without it, {@code DELETE /preferences/views/{id}} would delete
 * whatever uuid it was handed, and a user could remove somebody else's saved view by
 * guessing one. The key is never taken from the request — it is derived from the caller's
 * own claims — so scoping every statement by it makes that class of bug unrepresentable.
 */
public interface AdminSavedViewRepository {

	@NonNull
	@SingleResult
	Publisher<? extends AdminSavedView> save(@Valid @NotNull @NonNull AdminSavedView view);

	@NonNull
	@SingleResult
	Publisher<? extends AdminSavedView> update(@Valid @NotNull @NonNull AdminSavedView view);

	/** Scoped by owner, so an id alone cannot reach another user's view. */
	@NonNull
	@SingleResult
	Publisher<? extends AdminSavedView> findByIdAndAdminKey(@NonNull UUID id,
		@NonNull String adminKey);

	@NonNull
	Publisher<AdminSavedView> findByAdminKeyOrderByNameAsc(@NonNull String adminKey);

	@NonNull
	Publisher<AdminSavedView> findByAdminKeyAndViewTypeOrderByNameAsc(@NonNull String adminKey,
		@NonNull String viewType);

	/** Drives the per-user quota. */
	@NonNull
	@SingleResult
	Publisher<Long> countByAdminKey(@NonNull String adminKey);

	@NonNull
	@SingleResult
	Publisher<Long> deleteByIdAndAdminKey(@NonNull UUID id, @NonNull String adminKey);
}
