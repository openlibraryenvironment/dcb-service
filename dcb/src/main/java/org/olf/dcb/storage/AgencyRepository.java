package org.olf.dcb.storage;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;

import org.olf.dcb.core.model.DataAgency;
import org.olf.dcb.core.model.DataHostLms;
import org.reactivestreams.Publisher;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.Collection;
import java.util.UUID;

public interface AgencyRepository {

	@NonNull
	@SingleResult
	Publisher<? extends DataAgency> save(@Valid @NotNull @NonNull DataAgency agency);

	@NonNull
	@SingleResult
	Publisher<DataAgency> persist(@Valid @NotNull @NonNull DataAgency agency);

	@NonNull
	@SingleResult
	Publisher<? extends DataAgency> update(@Valid @NotNull @NonNull DataAgency agency);

	@NonNull
	@SingleResult
	Publisher<DataAgency> findById(@NonNull UUID id);

	@NonNull
	@SingleResult
	Publisher<Boolean> existsById(@NonNull UUID id);

	@NonNull
	@SingleResult
	Publisher<Page<DataAgency>> queryAll(Pageable page);

	@NonNull
	Publisher<DataAgency> findOneByCode(String code);

	Publisher<DataAgency> queryAll();

	Publisher<Void> delete(UUID id);

	Publisher<Void> deleteByCode(@NotNull String code);

	// Find the ID Of the HostLms for this repository. Wanted findHostLmsById but that seems to cause problems.
	Publisher<UUID> findHostLmsIdById(@NonNull UUID id);

	@NonNull
	@SingleResult
	Publisher<DataHostLms> findHostLmsById(@NonNull UUID id);

	@Query(value = "SELECT * from agency where host_lms_id in (:hostLmsIds) order by name", nativeQuery = true)
	Publisher<DataAgency> findByHostLmsIds(@NonNull Collection<UUID> hostLmsIds);

	@Query(value = "SELECT host_lms_id from agency where code in (:agencyCodes) and host_lms_id is not null order by name", nativeQuery = true)
	Publisher<UUID> findHostLmsIdByAgencyCodes(@NonNull Collection<String> agencyCodes);
}
