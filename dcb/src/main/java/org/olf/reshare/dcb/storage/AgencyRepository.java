package org.olf.reshare.dcb.storage;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import org.olf.reshare.dcb.core.model.DataAgency;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.util.UUID;
public interface AgencyRepository {

        @NonNull
        @SingleResult
        Publisher<? extends DataAgency> save(@Valid @NotNull @NonNull DataAgency agency);

        @NonNull
        @SingleResult
        Publisher<? extends DataAgency> persist(@Valid @NotNull @NonNull DataAgency agency);

        @NonNull
        @SingleResult
        Publisher<? extends DataAgency> update(@Valid @NotNull @NonNull DataAgency agency);

        @NonNull
        @SingleResult
        Publisher<DataAgency> findById(@NonNull UUID id);

        @NonNull
        Publisher<DataAgency> findAll();

        Publisher<Void> delete(UUID id);

}
