package org.olf.dcb.storage;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;

import org.olf.dcb.core.model.AgencyGroup;
import org.reactivestreams.Publisher;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import reactor.core.publisher.Mono;
import jakarta.transaction.Transactional;


@Transactional
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
	Publisher<Page<AgencyGroup>> queryAll(Pageable page);

	@NonNull
	Publisher<AgencyGroup> findOneByCode(String code);

	Publisher<AgencyGroup> queryAll();

	Publisher<Void> delete(UUID id);

	Publisher<Void> deleteByCode(@NotNull String code);

        @SingleResult
        @NonNull
        default Publisher<AgencyGroup> saveOrUpdate(@Valid @NotNull AgencyGroup ag) {
                return Mono.from(this.existsById(ag.getId()))
                        .flatMap( update -> Mono.from( update ? this.update(ag) : this.save(ag)) )
                        ;
        }

}
