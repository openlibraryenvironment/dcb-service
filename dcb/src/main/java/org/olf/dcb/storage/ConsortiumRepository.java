package org.olf.dcb.storage;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.olf.dcb.core.model.*;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface ConsortiumRepository {
	@NonNull
	@SingleResult
	Publisher<? extends Consortium> save(@Valid @NotNull @NonNull Consortium consortium);

	@NonNull
	@SingleResult
	Publisher<? extends Consortium> persist(@Valid @NotNull @NonNull Consortium consortium);

	@NonNull
	@SingleResult
	Publisher<? extends Consortium> update(@Valid @NotNull @NonNull Consortium consortium);

	@NonNull
	@SingleResult
	Publisher<? extends Consortium> findById(@NonNull UUID id);

	@NonNull
	@SingleResult
	Publisher<Consortium> findOneByName(@NonNull String name);

	@NonNull
	@SingleResult
	Publisher<Consortium> findOneByLibraryGroup(@NonNull LibraryGroup libraryGroup);



	@NonNull
	@SingleResult
	Publisher<Boolean> existsById(@NonNull UUID id);

	@NonNull
	@SingleResult
	Publisher<Page<Consortium>> queryAll(Pageable page);

	@NonNull
	Publisher<Consortium> queryAll();

	Publisher<Void> delete(UUID id);

	@SingleResult
	@NonNull
	default Publisher<Consortium> saveOrUpdate(@Valid @NotNull Consortium consortium) {
		return Mono.from(this.existsById(consortium.getId()))
			.flatMap( update -> Mono.from( update ? this.update(consortium) : this.save(consortium)) )
			;
	}

}
