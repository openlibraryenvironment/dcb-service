package org.olf.reshare.dcb.storage;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import org.olf.reshare.dcb.core.model.ProcessState;
import org.reactivestreams.Publisher;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.util.UUID;

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

	Publisher<Void> delete(UUID id);
}
