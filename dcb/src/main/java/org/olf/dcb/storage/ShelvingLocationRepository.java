package org.olf.dcb.storage;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.data.annotation.Join;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;

import org.olf.dcb.core.model.ShelvingLocation;
import org.reactivestreams.Publisher;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.util.UUID;

public interface ShelvingLocationRepository {

	@NonNull
	@SingleResult
	Publisher<? extends ShelvingLocation> save(@Valid @NotNull @NonNull ShelvingLocation agency);

	@NonNull
	@SingleResult
	Publisher<? extends ShelvingLocation> persist(@Valid @NotNull @NonNull ShelvingLocation agency);

	@NonNull
	@SingleResult
	Publisher<? extends ShelvingLocation> update(@Valid @NotNull @NonNull ShelvingLocation agency);

	@NonNull
	@SingleResult
	Publisher<ShelvingLocation> findById(@NonNull UUID id);

	@NonNull
	@SingleResult
	@Join(value = "agency", type = Join.Type.LEFT_FETCH)
	Publisher<ShelvingLocation> findOneByCode(@NonNull String code);

	@NonNull
	@SingleResult
	Publisher<Boolean> existsById(@NonNull UUID id);

	@NonNull
	@SingleResult
	Publisher<Page<ShelvingLocation>> findAll(Pageable page);

	@NonNull
	Publisher<? extends ShelvingLocation> findAll();

	Publisher<Void> delete(UUID id);

	Publisher<Void> deleteByCode(@NotNull String code);
}
