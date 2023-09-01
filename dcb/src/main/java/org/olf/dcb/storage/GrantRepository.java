package org.olf.dcb.storage;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;

import org.olf.dcb.core.model.Grant;
import org.reactivestreams.Publisher;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import reactor.core.publisher.Mono;


public interface GrantRepository {

	@NonNull
	@SingleResult
	Publisher<? extends Grant> save(@Valid @NotNull @NonNull Grant agency);

	@NonNull
	@SingleResult
	Publisher<Grant> persist(@Valid @NotNull @NonNull Grant agency);

	@NonNull
	@SingleResult
	Publisher<? extends Grant> update(@Valid @NotNull @NonNull Grant agency);

	@NonNull
	@SingleResult
	Publisher<Grant> findById(@NonNull UUID id);

	@NonNull
	@SingleResult
	Publisher<Boolean> existsById(@NonNull UUID id);

	@NonNull
	@SingleResult
	Publisher<Page<Grant>> queryAll(Pageable page);

	Publisher<Grant> queryAll();

	Publisher<Void> delete(UUID id);

        @SingleResult
        @NonNull
        default Publisher<Grant> saveOrUpdate(@Valid @NotNull Grant g) {
                return Mono.from(this.existsById(g.getId()))
                        .flatMap( update -> Mono.from(update ? this.update(g) : this.save(g)) )
                ;
        }

}
