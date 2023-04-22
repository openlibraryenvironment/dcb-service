package org.olf.reshare.dcb.storage;

import java.util.UUID;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import org.olf.reshare.dcb.core.model.BibIdentifier;
import org.reactivestreams.Publisher;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.async.annotation.SingleResult;

public interface BibIdentifierRepository {

    @NonNull
    @SingleResult
    Publisher<? extends BibIdentifier> save(@Valid @NotNull @NonNull BibIdentifier bibIdentifier);

    @NonNull
    @SingleResult
    Publisher<? extends BibIdentifier> update(@Valid @NotNull @NonNull BibIdentifier bibIdentifier);

    @NonNull
    @SingleResult
    Publisher<BibIdentifier> findById(@NonNull UUID id);

    @NonNull
    Publisher<BibIdentifier> findAll();

    @NonNull
    @SingleResult
    Publisher<Boolean> existsById(@NonNull UUID id);

    @NonNull
    @SingleResult
    Publisher<Void> delete(@NonNull UUID id);

    @NonNull
    @SingleResult
    Publisher<Void> cleanUp();

    @NonNull
    @SingleResult
    Publisher<Void> commit();
}

