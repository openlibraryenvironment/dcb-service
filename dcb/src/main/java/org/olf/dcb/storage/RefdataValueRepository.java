package org.olf.dcb.storage;

import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import org.olf.dcb.core.model.RefdataValue;
import org.reactivestreams.Publisher;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;

public interface RefdataValueRepository {

    @NonNull
    @SingleResult
    Publisher<? extends RefdataValue> save(@Valid @NotNull @NonNull RefdataValue agency);

    @NonNull
    @SingleResult
    Publisher<? extends RefdataValue> persist(@Valid @NotNull @NonNull RefdataValue agency);

    @NonNull
    @SingleResult
    Publisher<? extends RefdataValue> update(@Valid @NotNull @NonNull RefdataValue agency);

    @NonNull
    @SingleResult
    Publisher<RefdataValue> findById(@NonNull UUID id);

    @NonNull
    @SingleResult
    Publisher<Boolean> existsById(@NonNull UUID id);

    @NonNull
    @SingleResult
    Publisher<Page<RefdataValue>> queryAll(Pageable page);

    @NonNull
    Publisher<? extends RefdataValue> queryAll();

    Publisher<Void> delete(UUID id);

}
