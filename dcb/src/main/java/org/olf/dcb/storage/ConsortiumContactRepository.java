package org.olf.dcb.storage;

import java.util.Collection;
import java.util.UUID;

import org.olf.dcb.core.model.Consortium;
import org.olf.dcb.core.model.ConsortiumContact;
import org.reactivestreams.Publisher;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import reactor.core.publisher.Mono;

public interface ConsortiumContactRepository {

	@NonNull
	@SingleResult
	Publisher<? extends ConsortiumContact> save(@Valid @NotNull @NonNull ConsortiumContact consortiumContact);

	@NonNull
	@SingleResult
	Publisher<ConsortiumContact> persist(@Valid @NotNull @NonNull ConsortiumContact consortiumContact);

	@NonNull
	@SingleResult
	Publisher<? extends ConsortiumContact> update(@Valid @NotNull @NonNull ConsortiumContact consortiumContact);

	@NonNull
	@SingleResult
	Publisher<ConsortiumContact> findById(@NonNull UUID id);

	@NonNull
	@SingleResult
	Publisher<Boolean> existsById(@NonNull UUID id);

	@NonNull
	@SingleResult
	Publisher<Page<ConsortiumContact>> queryAll(Pageable page);

	Publisher<Page<ConsortiumContact>> findAll(@Valid Pageable pageable);

	@Query(value = "SELECT * from consortium_contact where consortium_id in (:consortiumIds)", nativeQuery = true)
	Publisher<ConsortiumContact> findByConsortiumIds(@NonNull Collection<UUID> consortiumIds);

	@NonNull
	Publisher<? extends ConsortiumContact> queryAll();

	Publisher<Void> delete(UUID id);

	Publisher<Void> deleteAllByConsortiumId(UUID id);

	@SingleResult
	@NonNull
	default Publisher<ConsortiumContact> saveOrUpdate(@Valid @NotNull ConsortiumContact lc) {
		return Mono.from(this.existsById(lc.getId()))
			.flatMap(update -> Mono.from(update ? this.update(lc) : this.save(lc)))
			;
	}


	Publisher<ConsortiumContact> findByConsortium(@NotNull Consortium consortium);
}
