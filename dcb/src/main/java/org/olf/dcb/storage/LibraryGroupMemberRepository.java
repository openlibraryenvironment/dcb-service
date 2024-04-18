package org.olf.dcb.storage;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;

import org.olf.dcb.core.model.*;
import org.reactivestreams.Publisher;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

import reactor.core.publisher.Mono;


public interface LibraryGroupMemberRepository {

	@NonNull
	@SingleResult
	Publisher<? extends LibraryGroupMember> save(@Valid @NotNull @NonNull LibraryGroupMember libraryGroupMember);

	@NonNull
	@SingleResult
	Publisher<LibraryGroupMember> persist(@Valid @NotNull @NonNull LibraryGroupMember libraryGroupMember);

	@NonNull
	@SingleResult
	Publisher<? extends LibraryGroupMember> update(@Valid @NotNull @NonNull LibraryGroupMember libraryGroupMember);

	@NonNull
	@SingleResult
	Publisher<LibraryGroupMember> findById(@NonNull UUID id);

	@NonNull
	@SingleResult
	Publisher<Boolean> existsById(@NonNull UUID id);

	@NonNull
	@SingleResult
	Publisher<Page<LibraryGroupMember>> queryAll(Pageable page);

	Publisher<Page<LibraryGroupMember>> findAll(@Valid Pageable pageable);


	@NonNull
	Publisher<? extends LibraryGroupMember> queryAll();

	Publisher<Void> delete(UUID id);

	@SingleResult
	@NonNull
	default Publisher<LibraryGroupMember> saveOrUpdate(@Valid @NotNull LibraryGroupMember gm) {
		return Mono.from(this.existsById(gm.getId()))
			.flatMap( update -> Mono.from( update ? this.update(gm) : this.save(gm)) )
			;
	}

	Publisher<LibraryGroupMember> findByLibraryGroup(LibraryGroup libraryGroup);

	Publisher<LibraryGroupMember> findByLibrary(Library library);
	// if this only returns one, change to 'find All By Library'

	@NonNull
	@SingleResult
	Publisher<LibraryGroupMember> queryAllByLibrary(@NotNull Library library);

	// may change to 'findAllBy'


	@NonNull
	@SingleResult
	Publisher<LibraryGroupMember> queryAllByLibraryGroup(@NotNull LibraryGroup libraryGroup);


	// Get the library for this GroupMember
	Publisher<Library> findLibraryById(UUID id);

	Publisher<LibraryGroup> findLibraryGroupById(UUID id);


}
