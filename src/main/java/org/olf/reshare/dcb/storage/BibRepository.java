package org.olf.reshare.dcb.storage;

import java.util.UUID;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

import org.olf.reshare.dcb.bib.model.BibRecord;
import org.reactivestreams.Publisher;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.async.annotation.SingleResult;

public interface BibRepository {

	@NonNull
	@SingleResult
	Publisher<? extends BibRecord> save(@Valid @NotNull @NonNull BibRecord bibRecord);

	@NotNull
	@SingleResult
	Publisher<BibRecord> findById(@NonNull UUID id);

	@NotNull
	Publisher<BibRecord> findAll();

	public default void cleanUp() {
	};

	public default void commit() {
	}
}
