package org.olf.dcb.storage;

import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import org.olf.dcb.core.model.LocationSymbol;
import org.reactivestreams.Publisher;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;

public interface LocationSymbolRepository {

	@NonNull
	@SingleResult
	Publisher<? extends LocationSymbol> save(@Valid @NotNull @NonNull LocationSymbol locationSymbol);

	@NonNull
	@SingleResult
	Publisher<? extends LocationSymbol> update(@Valid @NotNull @NonNull LocationSymbol locationSymbol);

	@NonNull
	@SingleResult
	Publisher<LocationSymbol> findById(@NotNull UUID id);

	@NonNull
	@SingleResult
	Publisher<Boolean> existsById(@NonNull UUID id);

	@NonNull
	@SingleResult
	Publisher<Page<LocationSymbol>> queryAll(Pageable page);

	@NonNull
	Publisher<LocationSymbol> queryAll();

	Publisher<Void> delete(UUID id);

}
