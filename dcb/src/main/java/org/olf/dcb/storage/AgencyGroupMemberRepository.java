package org.olf.dcb.storage;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;

import org.olf.dcb.core.model.DataAgency;
import org.olf.dcb.core.model.AgencyGroup;
import org.olf.dcb.core.model.AgencyGroupMember;
import org.reactivestreams.Publisher;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;


public interface AgencyGroupMemberRepository {

	@NonNull
	@SingleResult
	Publisher<? extends AgencyGroupMember> save(@Valid @NotNull @NonNull AgencyGroupMember agencyGroup);

	@NonNull
	@SingleResult
	Publisher<AgencyGroupMember> persist(@Valid @NotNull @NonNull AgencyGroupMember agencyGroup);

	@NonNull
	@SingleResult
	Publisher<? extends AgencyGroupMember> update(@Valid @NotNull @NonNull AgencyGroupMember agencyGroup);

	@NonNull
	@SingleResult
	Publisher<AgencyGroupMember> findById(@NonNull UUID id);

	@NonNull
	@SingleResult
	Publisher<Boolean> existsById(@NonNull UUID id);

	@NonNull
	@SingleResult
	Publisher<Page<AgencyGroupMember>> queryAll(Pageable page);

	@NonNull
	Publisher<? extends AgencyGroupMember> queryAll();

	Publisher<Void> delete(UUID id);

        @SingleResult
        @NonNull
        default Publisher<AgencyGroupMember> saveOrUpdate(@Valid @NotNull AgencyGroupMember agm) {
                return Mono.from(this.existsById(agm.getId()))
                        .flatMap( update -> Mono.from( update ? this.update(agm) : this.save(agm)) )
                        ;
        }

        Publisher<AgencyGroupMember> findByGroup(AgencyGroup group);

        Publisher<AgencyGroupMember> findByAgency(DataAgency agency);

        // Get the agency for this AgencyGroupMember
        Publisher<DataAgency> findAgencyById(UUID id);

        Publisher<AgencyGroup> findGroupById(UUID id);


}
