package org.olf.reshare.dcb.storage;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Sort;

import org.olf.reshare.dcb.core.model.Location;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.util.UUID;

public interface LocationRepository {

        @NonNull
        @SingleResult
        Publisher<? extends Location> save(@Valid @NotNull @NonNull Location location);

        @NonNull
        @SingleResult
        Publisher<Location> findById(@NotNull UUID id);

        @NonNull
        Publisher<Location> findAll();

        Publisher<Void> delete(UUID id);

}

