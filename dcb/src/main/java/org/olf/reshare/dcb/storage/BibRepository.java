package org.olf.reshare.dcb.storage;

import java.util.UUID;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

import org.olf.reshare.dcb.model.BibRecord;
import org.reactivestreams.Publisher;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.async.annotation.SingleResult;

public interface BibRepository {

	@NonNull
	@SingleResult
	Publisher<? extends BibRecord> save(@Valid @NotNull @NonNull BibRecord bibRecord);
	
	@NonNull
	@SingleResult
	Publisher<? extends BibRecord> update(@Valid @NotNull @NonNull BibRecord bibRecord);

	@NonNull
	@SingleResult
	Publisher<BibRecord> findById(@NonNull UUID id);

	@NonNull
	Publisher<BibRecord> findAll();
	
	@NonNull
	@SingleResult
	Publisher<Boolean> existsById(@NonNull UUID id);

	public default void cleanUp() {
	};

	public default void commit() {
	}
}
