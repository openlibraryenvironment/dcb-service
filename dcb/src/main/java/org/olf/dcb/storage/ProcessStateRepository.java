package org.olf.dcb.storage;

import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import org.olf.dcb.core.model.ProcessState;
import org.reactivestreams.Publisher;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.async.annotation.SingleResult;

public interface ProcessStateRepository {

	@NonNull
	@SingleResult
	Publisher<? extends ProcessState> save(@Valid @NotNull @NonNull ProcessState processState);

	@NonNull
	@SingleResult
	Publisher<? extends ProcessState> persist(@Valid @NotNull @NonNull ProcessState processState);

	@NonNull
	@SingleResult
	Publisher<? extends ProcessState> update(@Valid @NotNull @NonNull ProcessState processState);

	@NonNull
	@SingleResult
	Publisher<ProcessState> findById(@NonNull UUID id);

	@NonNull
	@SingleResult
	Publisher<Boolean> existsById(@NonNull UUID id);

	@NonNull
	Publisher<ProcessState> findAll();

	Publisher<Void> delete(UUID id);
}
