package org.olf.dcb.storage;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;

import org.olf.dcb.core.model.AgencyGroupMember;
import org.reactivestreams.Publisher;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public interface AgencyGroupMemberRepository {

	@NonNull
	@SingleResult
	Publisher<? extends AgencyGroupMember> save(@Valid @NotNull @NonNull AgencyGroupMember agencyGroup);

	@NonNull
	@SingleResult
	Publisher<? extends AgencyGroupMember> persist(@Valid @NotNull @NonNull AgencyGroupMember agencyGroup);

	@NonNull
	@SingleResult
	Publisher<? extends AgencyGroupMember> update(@Valid @NotNull @NonNull AgencyGroupMember agencyGroup);

	@NonNull
	@SingleResult
	Publisher<? extends AgencyGroupMember> findById(@NonNull UUID id);

	@NonNull
	@SingleResult
	Publisher<Boolean> existsById(@NonNull UUID id);

	@NonNull
	@SingleResult
	Publisher<Page<AgencyGroupMember>> queryAll(Pageable page);

	@NonNull
	Publisher<? extends AgencyGroupMember> queryAll();

	Publisher<Void> delete(UUID id);
}
