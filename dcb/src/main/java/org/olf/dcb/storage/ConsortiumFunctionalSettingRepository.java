package org.olf.dcb.storage;

import java.util.Collection;
import java.util.UUID;

import org.olf.dcb.core.model.Consortium;
import org.olf.dcb.core.model.ConsortiumFunctionalSetting;
import org.olf.dcb.core.model.FunctionalSetting;
import org.reactivestreams.Publisher;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import reactor.core.publisher.Mono;

public interface ConsortiumFunctionalSettingRepository {

	@NonNull
	@SingleResult
	Publisher<? extends ConsortiumFunctionalSetting> save(@Valid @NotNull @NonNull ConsortiumFunctionalSetting consortiumFunctionalSetting);

	@NonNull
	@SingleResult
	Publisher<ConsortiumFunctionalSetting> persist(@Valid @NotNull @NonNull ConsortiumFunctionalSetting consortiumFunctionalSetting);

	@NonNull
	@SingleResult
	Publisher<? extends ConsortiumFunctionalSetting> update(@Valid @NotNull @NonNull ConsortiumFunctionalSetting consortiumFunctionalSetting);

	@NonNull
	@SingleResult
	Publisher<ConsortiumFunctionalSetting> findById(@NonNull UUID id);

	@NonNull
	@SingleResult
	Publisher<ConsortiumFunctionalSetting> findByConsortiumAndFunctionalSetting(@NonNull Consortium consortium, FunctionalSetting functionalSetting);

	@NonNull
	@SingleResult
	Publisher<Boolean> existsById(@NonNull UUID id);

	@NonNull
	@SingleResult
	Publisher<Page<ConsortiumFunctionalSetting>> queryAll(Pageable page);

	Publisher<Page<ConsortiumFunctionalSetting>> findAll(@Valid Pageable pageable);

	@Query(value = "SELECT * from functional_setting where consortium_id in (:consortiumIds)", nativeQuery = true)
	Publisher<ConsortiumFunctionalSetting> findByConsortiumIds(@NonNull Collection<UUID> consortiumIds);

	@NonNull
	Publisher<? extends ConsortiumFunctionalSetting> queryAll();

	Publisher<Void> delete(UUID id);

	Publisher<Void> deleteAllByConsortiumId(UUID id);

	@SingleResult
	@NonNull
	default Publisher<ConsortiumFunctionalSetting> saveOrUpdate(@Valid @NotNull ConsortiumFunctionalSetting lc) {
		return Mono.from(this.existsById(lc.getId()))
			.flatMap(update -> Mono.from(update ? this.update(lc) : this.save(lc)))
			;
	}


	Publisher<ConsortiumFunctionalSetting> findByConsortium(@NotNull Consortium consortium);
}
