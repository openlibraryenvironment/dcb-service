package org.olf.dcb.storage;

import java.util.Collection;
import java.util.UUID;

import org.olf.dcb.core.model.DataHostLms;
import org.olf.dcb.core.model.Location;
import org.olf.dcb.core.model.DataAgency;
import org.reactivestreams.Publisher;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import io.micronaut.data.annotation.Query;
import reactor.core.publisher.Mono;

public interface LocationRepository {

	@NonNull
	@SingleResult
	Publisher<? extends Location> save(@Valid @NotNull @NonNull Location location);

	@NonNull
	@SingleResult
	Publisher<? extends Location> update(@Valid @NotNull @NonNull Location location);

	@NonNull
	@SingleResult
	Publisher<Location> findById(@NotNull @NonNull UUID id);

	@NonNull
	@SingleResult
	Publisher<Boolean> existsById(@NotNull @NonNull UUID id);

	@NonNull
	@SingleResult
	Publisher<Page<Location>> queryAll(Pageable page);

	@NonNull
	@SingleResult
	Publisher<Page<Location>> queryAllByType(String type, Pageable page);

	@NonNull
	Publisher<Location> queryAll();

	@NonNull
	@SingleResult
	Publisher<Location> findOneByCode(@NotNull @NonNull String code);

	Publisher<Void> delete(UUID id);

	@NonNull
	@SingleResult
	Publisher<Location> queryAllByAgency(@NotNull DataAgency agency);

	@Query(value = "SELECT l.*, CASE WHEN a.code=:agency THEN 1 ELSE 0 END AS is_local from location l, agency a where l.agency_fk = a.id and is_pickup = true order by is_local desc, l.name", nativeQuery = true)
	Publisher<Location> getSortedPickupLocations(String agency);
	
	@Query(value = "SELECT * from location where host_system_id in (:hostLmsIds) order by name", nativeQuery = true)
	Publisher<Location> findByHostLmsIds(@NonNull Collection<UUID> hostLmsIds);
	
	@Query(value = "delete from location where host_system_id = :hostLmsId", nativeQuery = true)
	Publisher<Void> deleteByHostLmsId(@NonNull UUID hostLmsId);

	Publisher<Long> deleteByHostSystem(DataHostLms hostSystem);


	@NonNull
	@SingleResult
	Publisher<Boolean> existsByLocalIdAndHostSystem(@NotNull String localId, @NotNull DataHostLms hostSystem);

	@NonNull
	@SingleResult
	Publisher<Boolean> existsByCode(@NotNull String code);

	@SingleResult
	@NonNull
	default Publisher<Location> saveOrUpdate(@Valid @NotNull @NonNull Location location) {
		return Mono.from(this.existsById(location.getId()))
			.flux().concatMap(update -> Mono.from(update ? this.update(location) : this.save(location)));
	}
}
