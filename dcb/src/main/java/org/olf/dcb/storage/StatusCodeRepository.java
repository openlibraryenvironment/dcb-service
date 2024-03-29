package org.olf.dcb.storage;

import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import org.olf.dcb.core.model.StatusCode;
import org.reactivestreams.Publisher;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import reactor.core.publisher.Mono;

public interface StatusCodeRepository {

    @NonNull
    @SingleResult
    Publisher<? extends StatusCode> save(@Valid @NotNull @NonNull StatusCode sc);

    @NonNull
    @SingleResult
    Publisher<? extends StatusCode> persist(@Valid @NotNull @NonNull StatusCode sc);

    @NonNull
    @SingleResult
    Publisher<? extends StatusCode> update(@Valid @NotNull @NonNull StatusCode sc);

    @NonNull
    @SingleResult
    Publisher<StatusCode> findById(@NonNull UUID id);

    @NonNull
    @SingleResult
    Publisher<Boolean> existsById(@NonNull UUID id);

    @NonNull
    @SingleResult
    Publisher<Page<StatusCode>> queryAll(Pageable page);

    @NonNull
    Publisher<? extends StatusCode> queryAll();

    Publisher<Void> delete(UUID id);

    @SingleResult
    @NonNull
    default Publisher<StatusCode> saveOrUpdate(@Valid @NotNull StatusCode sc) {
      return Mono.from(this.existsById(sc.getId()))
                 .flatMap( update -> Mono.from(update ? this.update(sc) : this.save(sc)) )
      ;
    }
    
    Publisher<StatusCode> findAllByTracked( Boolean tracked );

}

