package org.olf.dcb.storage;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.Collection;
import java.util.UUID;
import org.olf.dcb.core.model.NumericRangeMapping;
import org.reactivestreams.Publisher;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import reactor.core.publisher.Mono;
import io.micronaut.data.annotation.Query;

public interface NumericRangeMappingRepository {

	@NonNull
	@SingleResult
	Publisher<? extends NumericRangeMapping> save(@Valid @NotNull @NonNull NumericRangeMapping numericRangeMapping);

	@NonNull
	@SingleResult
	Publisher<? extends NumericRangeMapping> update(@Valid @NotNull @NonNull NumericRangeMapping numericRangeMapping);

	@NonNull
	@SingleResult
	Publisher<NumericRangeMapping> findById(@NotNull UUID id);

	@SingleResult
	@Query(value = "SELECT nrm.mapped_value from numeric_range_mapping nrm where nrm.context=:context and nrm.domain=:domain and nrm.lower_bound <= :value and nrm.upper_bound >= :value and nrm.target_context=:target", nativeQuery = true)
	Publisher<String> findMappedValueFor(String context, String domain, String target, Long value);

	@NonNull
	@SingleResult
	@Query("SELECT * FROM numeric_range_mapping WHERE deleted = false OR deleted IS null")
	Publisher<Page<NumericRangeMapping>> queryAll(Pageable page);

	@NonNull
	@Query("SELECT * FROM numeric_range_mapping WHERE deleted = false OR deleted IS NULL")
	Publisher<NumericRangeMapping> queryAll();

	@Query(value = "SELECT * from numeric_range_mapping where context in (:contexts) order by context, domain, lower_bound", nativeQuery = true)
	Publisher<NumericRangeMapping> findByContexts(@NonNull Collection<String> contexts);

	Publisher<Void> delete(UUID id);

	@Query("UPDATE numeric_range_mapping SET deleted = true WHERE context =:context")
	Publisher<Long> markAsDeleted(@NotNull String context);

	@Query("UPDATE numeric_range_mapping SET deleted = true WHERE context =:context AND domain =:category")
	Publisher<Long> markAsDeleted(@NotNull String context, @NotNull String category);

	@SingleResult
	@NonNull
	default Publisher<NumericRangeMapping> saveOrUpdate(@Valid @NotNull @NonNull NumericRangeMapping nrm) {
		return Mono.from(this.existsById(nrm.getId()))
			.flatMap(update -> Mono.from(update ? this.update(nrm) : this.save(nrm)));
	}

	@NonNull
	@SingleResult
	Publisher<Boolean> existsById(@NonNull UUID id);

}

