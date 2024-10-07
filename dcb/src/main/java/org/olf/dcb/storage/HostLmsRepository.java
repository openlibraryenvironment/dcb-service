package org.olf.dcb.storage;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.olf.dcb.core.model.DataHostLms;
import org.reactivestreams.Publisher;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Vetoed;
import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import reactor.core.publisher.Mono;

public interface HostLmsRepository {
	@NonNull
	@SingleResult
	Publisher<? extends DataHostLms> save(@Valid @NotNull @NonNull DataHostLms hostLms);

	@NonNull
	@SingleResult
	Publisher<? extends DataHostLms> update(@Valid @NotNull @NonNull DataHostLms hostLms);

	@NonNull
	@SingleResult
	@Vetoed
	Publisher<DataHostLms> findById(@NonNull UUID id);

	@NonNull
	@SingleResult
	Publisher<Boolean> existsById(@NonNull UUID id);

	@NonNull
	@SingleResult
	Publisher<DataHostLms> findByCode(@NonNull String code);

	@NonNull
	@SingleResult
	Publisher<Page<DataHostLms>> queryAll(Pageable page);

	@NonNull
	Publisher<DataHostLms> queryAll();

	@Query(value = "SELECT * from host_lms where id in (:ids) order by name", nativeQuery = true)
	Publisher<DataHostLms> findByIds(@NonNull Collection<UUID> ids);

	Publisher<Void> delete(UUID id);

	default Mono<DataHostLms> saveOrUpdate(DataHostLms hostLMS) {
		return Mono.from(existsById(hostLMS.getId()))
			.flatMap(exists -> Mono.fromDirect(exists ? update(hostLMS) : save(hostLMS)));
	}
}
