package org.olf.dcb.storage;

import java.util.Collection;
import java.util.UUID;

import org.olf.dcb.core.model.DataHostLms;
import org.reactivestreams.Publisher;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.repository.jpa.criteria.QuerySpecification;
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
	public Publisher<DataHostLms> findById(@NonNull UUID id);

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

	// This query finds the cataloguing host lms (parent) if any of the supplied host lms ids (child) just have a circulation role 
	@Query(value = """
select *
from host_lms parentLms, host_lms childLms
where json_array_length(childlms.client_config::json->'roles') = 1 and
	  (childlms.client_config->'roles')::jsonb ? 'CIRCULATION' and
	  childlms.id in (:ids) and
	  (parentlms.client_config->'roles')::jsonb ? 'CATALOGUE' and
	  (childlms.client_config->'contextHierarchy')::jsonb ? parentlms.code""", nativeQuery = true)
	Publisher<DataHostLms> findParentsByIds(@NonNull Collection<UUID> ids);

	Publisher<Void> delete(UUID id);

	default Mono<DataHostLms> saveOrUpdate(DataHostLms hostLMS) {
		return Mono.from(existsById(hostLMS.getId()))
			.flatMap(exists -> Mono.fromDirect(exists ? update(hostLMS) : save(hostLMS)));
	}

	@NonNull
	@SingleResult
	Publisher<Page<DataHostLms>> findAll(QuerySpecification<DataHostLms> spec, Pageable pageable);
	
	@NonNull
	@SingleResult
	Publisher<Page<DataHostLms>> findAll(Pageable pageable);
}
