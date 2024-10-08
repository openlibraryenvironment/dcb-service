package org.olf.dcb.storage;

import java.util.Collection;
import java.util.UUID;

import org.olf.dcb.core.model.Library;
import org.olf.dcb.core.model.LibraryGroup;
import org.olf.dcb.core.model.LibraryGroupMember;
import org.reactivestreams.Publisher;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
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

	Publisher<Void> deleteByLibraryId(UUID id);

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


	@NonNull
	@SingleResult
	Publisher<LibraryGroupMember> queryAllByLibraryGroup(@NotNull LibraryGroup libraryGroup);


	// Get the library for this GroupMember
	Publisher<Library> findLibraryById(UUID id);

	Publisher<LibraryGroup> findLibraryGroupById(UUID id);

	@Query(value = "SELECT * from library_group_member where library_id in (:libraryIds)", nativeQuery = true)
	Publisher<LibraryGroupMember> findByLibraryIds(@NonNull Collection<UUID> libraryIds);
}
