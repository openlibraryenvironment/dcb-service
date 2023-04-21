package org.olf.reshare.dcb.storage;

import java.util.UUID;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

import org.olf.reshare.dcb.core.model.ShelvingLocation;
import org.reactivestreams.Publisher;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;

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
	Publisher<Boolean> existsById(@NonNull UUID id);

	@NonNull
	@SingleResult
	Publisher<Page<ShelvingLocation>> findAll(Pageable page);

	@NonNull
	Publisher<? extends ShelvingLocation> findAll();

	Publisher<Void> delete(UUID id);

}
