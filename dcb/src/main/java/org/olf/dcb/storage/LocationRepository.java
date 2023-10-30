package org.olf.dcb.storage;

import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import org.olf.dcb.core.model.Location;
import org.reactivestreams.Publisher;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;

public interface LocationRepository {

	@NonNull
	@SingleResult
	Publisher<? extends Location> save(@Valid @NotNull @NonNull Location location);

	@NonNull
	@SingleResult
	Publisher<? extends Location> update(@Valid @NotNull @NonNull Location location);

	@NonNull
	@SingleResult
	Publisher<Location> findById(@NotNull UUID id);

	@NonNull
	@SingleResult
	Publisher<Boolean> existsById(@NonNull UUID id);

	@NonNull
	@SingleResult
	Publisher<Page<Location>> queryAll(Pageable page);

	@NonNull
	@SingleResult
	Publisher<Page<Location>> queryAllByType(String type, Pageable page);

	@NonNull
	Publisher<Location> queryAll();

	@NonNull
	Publisher<Location> findOneByCode(String code);

	Publisher<Void> delete(UUID id);
}
