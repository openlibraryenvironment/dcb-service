package org.olf.reshare.dcb.storage;

import java.util.UUID;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

import org.olf.reshare.dcb.core.model.ReferenceValueMapping;
import org.reactivestreams.Publisher;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
public interface ReferenceValueMappingRepository {

    @NonNull
    @SingleResult
    Publisher<? extends ReferenceValueMapping> save(@Valid @NotNull @NonNull ReferenceValueMapping agency);

    @NonNull
    @SingleResult
    Publisher<? extends ReferenceValueMapping> persist(@Valid @NotNull @NonNull ReferenceValueMapping agency);

    @NonNull
    @SingleResult
    Publisher<? extends ReferenceValueMapping> update(@Valid @NotNull @NonNull ReferenceValueMapping agency);

    @NonNull
    @SingleResult
    Publisher<ReferenceValueMapping> findById(@NonNull UUID id);

    @NonNull
    @SingleResult
    Publisher<Boolean> existsById(@NonNull UUID id);

    @NonNull
    @SingleResult
    Publisher<Page<ReferenceValueMapping>> findAll(Pageable page);

    @NonNull
    Publisher<? extends ReferenceValueMapping> findAll();

    Publisher<Void> delete(UUID id);


	/**
	 * given a source category, context, value and target context look up a value.
	 * For example - given "PatronType", "DCB", "UG", "SANDBOX" - return "15"
	 * Which is the patron type to use for undergraduates in the SANDBOX system 
	 * (Where UG is the canonical DCB code for the undergraduate Patron Type)
	 */
	Publisher<ReferenceValueMapping> findByFromCategoryAndFromContextAndFromValueAndToContext(
		@NonNull String sourceCategory,
		@NonNull String sourceContext,
		@NonNull String sourceValue,
		@NonNull String targetContext);
}
