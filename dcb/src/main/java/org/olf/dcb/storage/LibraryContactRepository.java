package org.olf.dcb.storage;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;

import org.olf.dcb.core.model.*;
import org.olf.dcb.graphql.*;
import org.reactivestreams.Publisher;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

import reactor.core.publisher.Mono;


public interface LibraryContactRepository {

	@NonNull
	@SingleResult
	Publisher<? extends LibraryContact> save(@Valid @NotNull @NonNull LibraryContact libraryContact);

	@NonNull
	@SingleResult
	Publisher<LibraryContact> persist(@Valid @NotNull @NonNull LibraryContact libraryContact);

	@NonNull
	@SingleResult
	Publisher<? extends LibraryContact> update(@Valid @NotNull @NonNull LibraryContact libraryContact);

	@NonNull
	@SingleResult
	Publisher<LibraryContact> findById(@NonNull UUID id);

	@NonNull
	@SingleResult
	Publisher<Boolean> existsById(@NonNull UUID id);

	@NonNull
	@SingleResult
	Publisher<Page<LibraryContact>> queryAll(Pageable page);

	Publisher<Page<LibraryContact>> findAll(@Valid Pageable pageable);


	@NonNull
	Publisher<? extends LibraryContact> queryAll();

	Publisher<Void> delete(UUID id);

	Publisher<Void> deleteAllByLibraryId(UUID id);

	@SingleResult
	@NonNull
	default Publisher<LibraryContact> saveOrUpdate(@Valid @NotNull LibraryContact lc) {
		return Mono.from(this.existsById(lc.getId()))
			.flatMap( update -> Mono.from( update ? this.update(lc) : this.save(lc)) )
			;
	}


	Publisher<LibraryContact> findByLibrary(@NotNull Library library);

	@NonNull
	@SingleResult
	Publisher<LibraryContact> queryAllByLibrary(@NotNull Library library);

	@NonNull
	@SingleResult
	Publisher<LibraryContact> findAllByLibrary(@NotNull Library library);

	@NonNull
	@SingleResult
	Publisher<LibraryContact> findAllByLibraryId(@NotNull UUID libraryId);

	@NonNull
	@SingleResult
	Publisher<LibraryContact> findAllByPerson(@NotNull Person person);



	// may change to 'findAllBy'

}
