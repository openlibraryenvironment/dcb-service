package org.olf.reshare.dcb.storage;

import java.util.UUID;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

import org.olf.reshare.dcb.core.model.PatronRequest;
import org.reactivestreams.Publisher;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.async.annotation.SingleResult;

public interface PatronRequestRepository {
	@NonNull
	@SingleResult
	Publisher<? extends PatronRequest> save(@Valid @NotNull @NonNull PatronRequest patronRequest);

	@NonNull
	@SingleResult
	Publisher<? extends PatronRequest> update(@Valid @NotNull @NonNull PatronRequest patronRequest);

	@NonNull
	@SingleResult
	Publisher<PatronRequest> findById(@NotNull UUID id);

	@NonNull
	Publisher<PatronRequest> findAll();

	Publisher<Void> delete(UUID id);
}
