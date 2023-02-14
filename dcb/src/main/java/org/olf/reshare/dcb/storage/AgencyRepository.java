package org.olf.reshare.dcb.storage;

import java.util.UUID;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

import org.olf.reshare.dcb.core.model.Agency;
import org.reactivestreams.Publisher;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;

public interface AgencyRepository {

	@NonNull
	@SingleResult
	Publisher<? extends Agency> save(@Valid @NotNull @NonNull Agency agency);

	@NonNull
	@SingleResult
	Publisher<? extends Agency> update(@Valid @NotNull @NonNull Agency agency);

	@NonNull
	@SingleResult
	Publisher<Agency> findById(@NonNull UUID id);

	@NonNull
	Publisher<Agency> findAll();

	@NonNull
	@SingleResult
	Publisher<Page<Agency>> findAll(Pageable page);

	@NonNull
	@SingleResult
	Publisher<Boolean> existsById(@NonNull UUID id);

	public default void cleanUp() {
	}

	;

	public default void commit() {
	}
}
