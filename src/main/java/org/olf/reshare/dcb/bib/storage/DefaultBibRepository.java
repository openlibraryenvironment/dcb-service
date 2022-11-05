package org.olf.reshare.dcb.bib.storage;

import java.util.Collections;
import java.util.Optional;
import java.util.function.Function;

import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

import org.olf.reshare.dcb.bib.model.BibRecord;
import org.reactivestreams.Publisher;

import io.micronaut.core.annotation.NonNull;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import one.microstream.concurrency.XThreads;
import one.microstream.storage.types.StorageManager;
import reactor.core.publisher.Flux;

@Singleton
public class DefaultBibRepository implements BibRepository {

	private final StorageManager bibStore;

	public DefaultBibRepository (@Named("bibs") StorageManager bibsStorageManager) {
		this.bibStore = bibsStorageManager;
	}

	@Override
	@NonNull
	@NotNull
	public BibRecord save ( @NonNull @NotNull @Valid BibRecord record ) {
		return execute( (bibData) -> {

			// Return the record.
			bibData.records().put( record.getId().toString(), record );
			return record;
		} );
	}
	
	@Override
	public void commit () {
		execute( (bibData) -> {
			return bibStore.store( bibData.records() );
		});
		cleanUp();
	}
	
	@Override
	public void cleanUp() {
		bibStore.issueFullGarbageCollection();
	}

	@Override
	@NonNull
	@NotNull
	public Optional<BibRecord> findById ( @NonNull @NotEmpty String id ) {
		return execute( (bibData) -> {

			// Return the record.
			return Optional.ofNullable( bibData.records().get( id ) );
			
		});
	}

	// Helper to wrap the supplied operation to ensure synchronization.
	protected <T> T execute ( @NotNull Function<BibDataRoot, T> action ) {
		return XThreads.executeSynchronized( () -> action.apply( (BibDataRoot)bibStore.root() ));
	}

	@Override
	@NotNull
	@NonNull
	public Publisher<BibRecord> getAll () {
		return execute( bibData -> {
			return Flux.fromIterable( Collections.unmodifiableCollection( bibData.records().values() ));
		});
	}

}
