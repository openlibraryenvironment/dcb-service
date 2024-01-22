package org.olf.dcb.storage;

import java.util.UUID;

import io.micronaut.data.annotation.Query;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import org.olf.dcb.core.model.ReferenceValueMapping;
import org.reactivestreams.Publisher;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import reactor.core.publisher.Mono;

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
    Publisher<Page<ReferenceValueMapping>> queryAll(Pageable page);

    @NonNull
    Publisher<? extends ReferenceValueMapping> queryAll();

		Publisher<Void> delete(UUID id);

		@Query("UPDATE reference_value_mapping SET deleted = true WHERE from_category = :fromCategory AND from_context = :fromContext")
		Publisher<Long> markAsDeleted(@NotNull String fromCategory, @NotNull String fromContext);

		// Find all deleted mappings. This method could be extended for a specific context / category in future if needed.
		@Query("SELECT * FROM reference_value_mapping WHERE deleted = true")
		Publisher<ReferenceValueMapping> findDeleted();

	/**
	 * given a source category, context, value and target context look up a value.
	 * For example - given "PatronType", "DCB", "UG", "SANDBOX" - return "15"
	 * Which is the patron type to use for undergraduates in the SANDBOX system 
	 * (Where UG is the canonical DCB code for the undergraduate Patron Type)
	 */

	Publisher<ReferenceValueMapping> findAllByFromCategory(
		@NonNull String fromCategory);

	Publisher<ReferenceValueMapping> findAllByFromCategoryAndFromContext(
		@NonNull String fromCategory,
		@NonNull String fromContext);

	Publisher<ReferenceValueMapping> findOneByFromCategoryAndFromContextAndFromValueAndToContext(
		@NonNull String sourceCategory,
		@NonNull String sourceContext,
		@NonNull String sourceValue,
		@NonNull String targetContext);

	Publisher<ReferenceValueMapping> findOneByFromCategoryAndFromContextAndFromValueAndToCategoryAndToContext(
		@NonNull String sourceCategory,
		@NonNull String sourceContext,
		@NonNull String sourceValue,
		@NonNull String targetCategory,
		@NonNull String targetContext);

	Publisher<ReferenceValueMapping> findOneByFromCategoryAndFromContextAndToCategoryAndToContextAndToValue(
		@NonNull String sourceCategory,
		@NonNull String sourceContext,
		@NonNull String targetCategory,
		@NonNull String targetContext,
		@NonNull String targetValue);

	@SingleResult
	@NonNull
	default Publisher<ReferenceValueMapping> saveOrUpdate(@Valid @NotNull @NonNull ReferenceValueMapping rvm) {
		return Mono.from(this.existsById(rvm.getId()))
			.flux().concatMap(update -> Mono.from(update ? this.update(rvm) : this.save(rvm)));
	  }
}
