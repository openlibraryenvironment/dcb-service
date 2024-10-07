package org.olf.dcb.storage;

import java.util.Collection;
import java.util.UUID;

import org.olf.dcb.core.model.Library;
import org.olf.dcb.core.model.LibraryContact;
import org.olf.dcb.core.model.Person;
import org.reactivestreams.Publisher;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
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

	@Query(value = "SELECT * from library_contact where library_id in (:libraryIds)", nativeQuery = true)
	Publisher<LibraryContact> findByLibraryIds(@NonNull Collection<UUID> libraryIds);

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
