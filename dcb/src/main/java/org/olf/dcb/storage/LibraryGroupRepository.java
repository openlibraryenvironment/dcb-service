package org.olf.dcb.storage;

import java.util.Collection;
import java.util.UUID;

import org.olf.dcb.core.model.LibraryGroup;
import org.reactivestreams.Publisher;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import reactor.core.publisher.Mono;


@Transactional
public interface LibraryGroupRepository {

	@NonNull
	@SingleResult
	Publisher<? extends LibraryGroup> save(@Valid @NotNull @NonNull LibraryGroup libraryGroup);

	@NonNull
	@SingleResult
	Publisher<? extends LibraryGroup> persist(@Valid @NotNull @NonNull LibraryGroup libraryGroup);

	@NonNull
	@SingleResult
	Publisher<? extends LibraryGroup> update(@Valid @NotNull @NonNull LibraryGroup libraryGroup);

	@NonNull
	@SingleResult
	Publisher<? extends LibraryGroup> findById(@NonNull UUID id);

	@NonNull
	@SingleResult
	Publisher<Boolean> existsById(@NonNull UUID id);

	@NonNull
	@SingleResult
	Publisher<Page<LibraryGroup>> queryAll(Pageable page);

	@NonNull
	Publisher<LibraryGroup> findOneByCode(String code);

	@SingleResult
	@NonNull
	Publisher<LibraryGroup> findOneByName(String name);

	@SingleResult
	@NonNull
	Publisher<LibraryGroup> findOneByNameAndTypeIgnoreCase(String name, String type);

	Publisher<LibraryGroup> queryAll();

	@Query(value = "SELECT * from library_group where id in (select library_group_id from library_group_member where library_id in (:libraryIds))", nativeQuery = true)
	Publisher<LibraryGroup> findByLibraryIds(@NonNull Collection<UUID> libraryIds);

	Publisher<Void> delete(UUID id);

	Publisher<Void> deleteByCode(@NotNull String code);

	// may need some consortial lookup function here too but we'll come back to that

	@SingleResult
	@NonNull
	default Publisher<LibraryGroup> saveOrUpdate(@Valid @NotNull LibraryGroup gr) {
		return Mono.from(this.existsById(gr.getId()))
			.flatMap( update -> Mono.from( update ? this.update(gr) : this.save(gr)) )
			;
	}

}
