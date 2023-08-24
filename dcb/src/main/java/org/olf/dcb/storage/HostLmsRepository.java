package org.olf.dcb.storage;

import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import org.olf.dcb.core.model.DataHostLms;
import org.reactivestreams.Publisher;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
public interface HostLmsRepository {

	@NonNull
	@SingleResult
	Publisher<? extends DataHostLms> save(@Valid @NotNull @NonNull DataHostLms hostLms);

	@NonNull
	@SingleResult
	Publisher<? extends DataHostLms> persist(@Valid @NotNull @NonNull DataHostLms hostLms);

	@NonNull
	@SingleResult
	Publisher<? extends DataHostLms> update(@Valid @NotNull @NonNull DataHostLms hostLms);

	@NonNull
	@SingleResult
	Publisher<DataHostLms> findById(@NonNull UUID id);

	@NonNull
	@SingleResult
	Publisher<Boolean> existsById(@NonNull UUID id);

	@NonNull
	@SingleResult
	Publisher<DataHostLms> findByName(@NonNull String name);

	@NonNull
	@SingleResult
	Publisher<DataHostLms> findByCode(@NonNull String code);

	@NonNull
	@SingleResult
	Publisher<Page<DataHostLms>> findAll(Pageable page);

	@NonNull
	Publisher<DataHostLms> findAll();

	Publisher<Void> delete(UUID id);
}
