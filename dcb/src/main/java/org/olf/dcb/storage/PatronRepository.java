package org.olf.dcb.storage;

import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import org.olf.dcb.core.model.Patron;
import org.reactivestreams.Publisher;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.async.annotation.SingleResult;

public interface PatronRepository {
	@NonNull
	@SingleResult
	Publisher<? extends Patron> save(@Valid @NotNull @NonNull Patron patron);

	@NonNull
	@SingleResult
	Publisher<Patron> findById(@NotNull UUID id);

	@NonNull
	Publisher<Patron> findAll();

	@NonNull
	Publisher<Void> delete(UUID id);
}
