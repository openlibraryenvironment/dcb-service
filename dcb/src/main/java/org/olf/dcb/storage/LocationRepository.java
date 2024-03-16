package org.olf.dcb.storage;

import java.util.UUID;

import org.olf.dcb.core.model.Location;
import org.olf.dcb.core.model.DataAgency;
import org.reactivestreams.Publisher;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.Query;

public interface LocationRepository {

	@NonNull
	@SingleResult
	Publisher<? extends Location> save(@Valid @NotNull @NonNull Location location);

	@NonNull
	@SingleResult
	Publisher<? extends Location> update(@Valid @NotNull @NonNull Location location);

	@NonNull
	@SingleResult
	Publisher<Location> findById(@NotNull UUID id);

	@NonNull
	@SingleResult
	Publisher<Boolean> existsById(@NonNull UUID id);

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
	Publisher<Location> findOneByCode(String code);

	Publisher<Void> delete(UUID id);

	@NonNull
	@SingleResult
	Publisher<Location> queryAllByAgency(@NotNull DataAgency agency);

/*
SELECT 
select l.id, l.name, a.name, a.code, CASE WHEN a.code='6cals' THEN 1 ELSE 0 END AS is_local 
from location l, agency a 
where l.agency_fk = a.id 
order by is_local desc, l.name; 
*/

	@Query(value = "SELECT l.*, CASE WHEN a.code=:agency THEN 1 ELSE 0 END AS is_local from location l, agency a where l.agency_fk = a.id and is_pickup = true order by is_local desc, l.name", nativeQuery = true)
  Publisher<Location> getSortedPickupLocations(String agency);
}
