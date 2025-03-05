package org.olf.dcb.storage;

import java.util.Collection;
import java.util.UUID;

import org.olf.dcb.core.model.FunctionalSetting;
import org.olf.dcb.core.model.FunctionalSettingType;
import org.reactivestreams.Publisher;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import reactor.core.publisher.Mono;

public interface FunctionalSettingRepository {
	@NonNull
	@SingleResult
	Publisher<? extends FunctionalSetting> save(@Valid @NotNull @NonNull FunctionalSetting functionalSetting);

	@NonNull
	@SingleResult
	Publisher<? extends FunctionalSetting> persist(@Valid @NotNull @NonNull FunctionalSetting functionalSetting);

	@NonNull
	@SingleResult
	Publisher<? extends FunctionalSetting> update(@Valid @NotNull @NonNull FunctionalSetting functionalSetting);

	@NonNull
	@SingleResult
	Publisher<? extends FunctionalSetting> findById(@NonNull UUID id);

	@NonNull
	@SingleResult
	Publisher<FunctionalSetting> findByName(@NonNull FunctionalSettingType name);

	@NonNull
	@SingleResult
	Publisher<Boolean> existsById(@NonNull UUID id);

	@NonNull
	@SingleResult
	Publisher<Page<FunctionalSetting>> queryAll(Pageable page);

	@NonNull
	Publisher<FunctionalSetting> queryAll();

	@Query(value = "SELECT * from functionalSetting where id in (:ids)", nativeQuery = true)
	Publisher<FunctionalSetting> findByIds(@NonNull Collection<UUID> ids);

	Publisher<Void> delete(UUID id);

	@SingleResult
	@NonNull
	default Publisher<FunctionalSetting> saveOrUpdate(@Valid @NotNull FunctionalSetting functionalSetting) {
		return Mono.from(this.existsById(functionalSetting.getId()))
			.flatMap( update -> Mono.from( update ? this.update(functionalSetting) : this.save(functionalSetting)) )
			;
	}

	// Note: This returns true when the functional setting is available and no records when it is not available
	@SingleResult
	@Query(value = """
select true
from functional_setting fs, agency a
where fs.name = :functionalSetting and
	  a.id = :agencyId and
	  fs.enabled = true and
	  exists (select 1 
		 	  from consortium c, consortium_functional_setting cfs
			  where cfs.consortium_id = c.id and
			  		cfs.functional_setting_id = fs.id and
			        c.library_group_id in (select library_group_id
										   from library_group_member
										   where library_id = (select id from library where agency_code = a.code)))""", nativeQuery = true)
	public Publisher<Boolean> isSettingEnabledForAgency(UUID agencyId, String functionalSetting);
}
