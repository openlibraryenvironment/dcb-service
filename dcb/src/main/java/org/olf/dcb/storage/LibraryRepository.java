package org.olf.dcb.storage;

import java.util.Collection;
import java.util.UUID;

import org.olf.dcb.core.model.Library;
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
public interface LibraryRepository {
	@NonNull
	@SingleResult
	Publisher<? extends Library> save(@Valid @NotNull @NonNull Library library);

	@NonNull
	@SingleResult
	Publisher<? extends Library> persist(@Valid @NotNull @NonNull Library library);

	@NonNull
	@SingleResult
	Publisher<? extends Library> update(@Valid @NotNull @NonNull Library library);

	@NonNull
	@SingleResult
	Publisher<? extends Library> findById(@NonNull UUID id);

	@NonNull
	@SingleResult
	Publisher<? extends Library> findByFullName(@NonNull String name);

	@NonNull
	@SingleResult
	Publisher<Boolean> existsById(@NonNull UUID id);

	@NonNull
	@SingleResult
	Publisher<Page<Library>> queryAll(Pageable page);

	@NonNull
	Publisher<Library> findOneByAgencyCode(String agencyCode);

	@Query(value = "SELECT * from library where agency_code in (:agencyCodes) order by full_name", nativeQuery = true)
	Publisher<Library> findByAgencyCodes(@NonNull Collection<String> agencyCodes);
	
	Publisher<Library> queryAll();

	Publisher<Void> delete(UUID id);

	Publisher<Void> deleteByAgencyCode(@NotNull String agencyCode);

	@SingleResult
	@NonNull
	default Publisher<Library> saveOrUpdate(@Valid @NotNull Library l) {
		return Mono.from(this.existsById(l.getId()))
			.flatMap( update -> Mono.from( update ? this.update(l) : this.save(l)) )
			;
	}
}
