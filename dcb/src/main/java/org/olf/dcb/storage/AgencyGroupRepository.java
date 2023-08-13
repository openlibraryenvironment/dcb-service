package org.olf.dcb.storage;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;

import org.olf.dcb.core.model.AgencyGroup;
import org.reactivestreams.Publisher;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.util.UUID;

public interface AgencyGroupRepository {

	@NonNull
	@SingleResult
	Publisher<? extends AgencyGroup> save(@Valid @NotNull @NonNull AgencyGroup agencyGroup);

	@NonNull
	@SingleResult
	Publisher<? extends AgencyGroup> persist(@Valid @NotNull @NonNull AgencyGroup agencyGroup);

	@NonNull
	@SingleResult
	Publisher<? extends AgencyGroup> update(@Valid @NotNull @NonNull AgencyGroup agencyGroup);

	@NonNull
	@SingleResult
	Publisher<? extends AgencyGroup> findById(@NonNull UUID id);

	@NonNull
	@SingleResult
	Publisher<Boolean> existsById(@NonNull UUID id);

	@NonNull
	@SingleResult
	Publisher<Page<AgencyGroup>> findAll(Pageable page);

	@NonNull
	Publisher<? extends AgencyGroup> findOneByCode(String code);

	@NonNull
	Publisher<? extends AgencyGroup> findAll();

	Publisher<Void> delete(UUID id);

	Publisher<Void> deleteByCode(@NotNull String code);
}
