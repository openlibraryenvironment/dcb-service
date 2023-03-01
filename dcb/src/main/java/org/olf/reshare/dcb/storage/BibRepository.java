package org.olf.reshare.dcb.storage;

import java.util.UUID;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

import org.olf.reshare.dcb.core.model.BibRecord;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;

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
	Publisher<Page<BibRecord>> findAll(Pageable page);

	@NonNull
	@SingleResult
	Publisher<Boolean> existsById(@NonNull UUID id);

	static final Publisher<Void> NOOP = new Publisher<Void>() {
		@Override
		public void subscribe(Subscriber<? super Void> s) {
			s.onSubscribe(null);
		}
		
	};
	
	public Publisher<Void> cleanUp();


	public Publisher<Void> commit();
}
