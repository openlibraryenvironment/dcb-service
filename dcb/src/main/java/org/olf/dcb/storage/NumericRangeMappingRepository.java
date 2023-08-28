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
import io.micronaut.data.annotation.Id;
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
        Publisher<NumericRangeMapping> queryAll();

        Publisher<Void> delete(UUID id);

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

