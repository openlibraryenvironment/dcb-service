package org.olf.dcb.storage;

import java.util.UUID;
import java.util.Collection;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import org.olf.dcb.core.model.BibIdentifier;
import org.olf.dcb.core.model.BibRecord;
import org.reactivestreams.Publisher;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Vetoed;
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
    Publisher<BibIdentifier> findAllByOwner(@NonNull BibRecord owner);

    @NonNull
    Publisher<BibIdentifier> queryAll();

    @NonNull
    @SingleResult
    Publisher<Boolean> existsById(@NonNull UUID id);

    @NonNull
    @SingleResult
    Publisher<Void> delete(@NonNull UUID id);
    
    @NonNull
    @SingleResult
    Publisher<Long> deleteAllByOwner(@NonNull BibRecord owner);

    @NonNull
    @SingleResult
    @Vetoed
    Publisher<Void> cleanUp();

    @NonNull
    @SingleResult
    @Vetoed
    Publisher<Void> commit();

    Publisher<Long> deleteAllByOwnerIdAndIdNotIn(@NotNull UUID bibId, @NotNull Collection<UUID> values);
}

