package org.olf.reshare.dcb.storage;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import org.olf.reshare.dcb.core.model.PatronRequest;
import org.reactivestreams.Publisher;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.util.UUID;

public interface PatronRequestRepository {

	@NonNull
	@SingleResult
	Publisher<? extends PatronRequest> save(@Valid @NotNull @NonNull PatronRequest PatronRequest);

	@NonNull
	@SingleResult
	Publisher<? extends PatronRequest> update(@Valid @NotNull @NonNull PatronRequest PatronRequest);

	@NonNull
	@SingleResult
	Publisher<PatronRequest> findById(@NonNull UUID id);

	@NonNull
	Publisher<PatronRequest> findAll();

	@NonNull
	@SingleResult
	Publisher<Page<PatronRequest>> findAll(Pageable page);

	@NonNull
	@SingleResult
	Publisher<Boolean> existsById(@NonNull UUID id);

	public default void cleanUp() {
	}

	;

	public default void commit() {
	}
}
