package org.olf.dcb.storage;

import java.util.Collection;
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
	@Query("SELECT * FROM reference_value_mapping WHERE deleted = false OR deleted IS NULL")
	Publisher<Page<ReferenceValueMapping>> queryAll(Pageable page);

	@NonNull
	@Query("SELECT * FROM reference_value_mapping WHERE deleted = false OR deleted IS NULL")
	Publisher<? extends ReferenceValueMapping> queryAll();

	Publisher<Void> delete(UUID id);

	// For when we want to delete all existing RVMs for a given Host LMS, and we're not bothered about category.
	@Query("UPDATE reference_value_mapping SET deleted = true WHERE (from_context = :context OR to_context = :context) AND NOT deleted = true")
	Publisher<Long> markAsDeleted(@NotNull String context);

	@Query("UPDATE reference_value_mapping SET deleted = true WHERE (from_context = :context OR to_context = :context) AND (from_category = :category OR to_category = :category) AND NOT deleted=true")
	Publisher<Long> markAsDeleted(@NotNull String context, @NotNull String category);

	// Find all deleted mappings. This method could be extended for a specific context / category in future if needed.
	@Query("SELECT * FROM reference_value_mapping WHERE deleted = true")
	Publisher<ReferenceValueMapping> findDeleted();

	/**
	 * given a source category, context, value and target context look up a value.
	 * For example - given "PatronType", "DCB", "UG", "SANDBOX" - return "15"
	 * Which is the patron type to use for undergraduates in the SANDBOX system
	 * (Where UG is the canonical DCB code for the undergraduate Patron Type)
	 */

	@Query("SELECT * FROM reference_value_mapping WHERE from_category = :fromCategory AND (deleted = false OR deleted IS NULL)")
	Publisher<ReferenceValueMapping> findAllByFromCategory(
		@NonNull String fromCategory);

	@Query("SELECT * FROM reference_value_mapping WHERE from_category = :fromCategory AND from_context = :fromContext AND (deleted = false OR deleted IS NULL)")
	Publisher<ReferenceValueMapping> findAllByFromCategoryAndFromContext(
		@NonNull String fromCategory,
		@NonNull String fromContext);

	@Query("SELECT * FROM reference_value_mapping WHERE from_category = :sourceCategory AND from_context = :sourceContext AND from_value = :sourceValue AND to_context = :targetContext AND (deleted = false OR deleted IS NULL) LIMIT 1")
	Publisher<ReferenceValueMapping> findOneByFromCategoryAndFromContextAndFromValueAndToContext(
		@NonNull String sourceCategory,
		@NonNull String sourceContext,
		@NonNull String sourceValue,
		@NonNull String targetContext);
	@Query("SELECT * FROM reference_value_mapping WHERE from_category = :sourceCategory AND from_context = :sourceContext AND from_value = :sourceValue AND to_category = :targetCategory AND to_context = :targetContext AND (deleted = false OR deleted IS NULL) LIMIT 1")
	Publisher<ReferenceValueMapping> findOneByFromCategoryAndFromContextAndFromValueAndToCategoryAndToContext(
		@NonNull String sourceCategory,
		@NonNull String sourceContext,
		@NonNull String sourceValue,
		@NonNull String targetCategory,
		@NonNull String targetContext);

	@Query("SELECT * FROM reference_value_mapping WHERE from_category = :sourceCategory AND from_context = :sourceContext AND to_category = :targetCategory AND to_context = :targetContext AND to_value = :targetValue AND (deleted = false OR deleted IS NULL) LIMIT 1")
	Publisher<ReferenceValueMapping> findOneByFromCategoryAndFromContextAndToCategoryAndToContextAndToValue(
		@NonNull String sourceCategory,
		@NonNull String sourceContext,
		@NonNull String targetCategory,
		@NonNull String targetContext,
		@NonNull String targetValue);

	@Query(value = "SELECT * from reference_value_mapping where from_context in (:contexts) or to_context in (:contexts) order by from_context, from_category, from_value", nativeQuery = true)
	Publisher<ReferenceValueMapping> findByContexts(@NonNull Collection<String> contexts);

	// Actually delete all the records for the specified context and not just mark them as deleted
	@Query(value = "delete from reference_value_mapping where from_context in (:context) or to_context in (:context)", nativeQuery = true)
	Publisher<Void> deleteByContext(@NonNull String context);

	Publisher<ReferenceValueMapping> findByFromValueAndDeletedFalseAndFromContextAndFromCategory(
		String fromValue, String fromContext, String fromCategory);

	Publisher<ReferenceValueMapping> findByFromValueAndDeletedFalseAndToContextAndToCategory(
		String fromValue, String toContext, String toCategory);
	
	@SingleResult
	@NonNull
	default Publisher<ReferenceValueMapping> saveOrUpdate(@Valid @NotNull @NonNull ReferenceValueMapping rvm) {
		return Mono.from(this.existsById(rvm.getId()))
			.flux().concatMap(update -> Mono.from(update ? this.update(rvm) : this.save(rvm)));
	}
}
