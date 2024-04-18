package org.olf.dcb.storage;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import jakarta.transaction.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.olf.dcb.core.model.*;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

import java.util.UUID;

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
	Publisher<Boolean> existsById(@NonNull UUID id);

	@NonNull
	@SingleResult
	Publisher<Page<Library>> queryAll(Pageable page);

	@NonNull
	Publisher<Library> findOneByAgencyCode(String agencyCode);

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
