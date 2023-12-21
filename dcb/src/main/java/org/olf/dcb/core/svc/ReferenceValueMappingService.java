package org.olf.dcb.core.svc;

import static services.k_int.utils.ReactorUtils.consumeOnSuccess;

import org.olf.dcb.core.model.ReferenceValueMapping;
import org.olf.dcb.storage.ReferenceValueMappingRepository;

import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Singleton
public class ReferenceValueMappingService {
	private final ReferenceValueMappingRepository repository;

	public ReferenceValueMappingService(ReferenceValueMappingRepository repository) {
		this.repository = repository;
	}

	public Mono<ReferenceValueMapping> findMapping(String sourceCategory,
		String sourceContext, String sourceValue, String targetCategory, String targetContext) {

		log.debug("Attempting to find mapping from category: {}, from context: {}, source value: {}, to category: {}, to context: {}",
			sourceCategory, sourceContext, sourceValue, targetCategory, targetContext);

		return Mono.from(repository.findOneByFromCategoryAndFromContextAndFromValueAndToCategoryAndToContext(
				sourceCategory, sourceContext, sourceValue, targetCategory, targetContext))
			.doOnSuccess(consumeOnSuccess(
				() -> log.warn("No mapping found for from category: {}, from context: {}, source value: {}, to category: {}, to context: {}",
					sourceCategory, sourceContext, sourceValue, targetCategory, targetContext),
				mapping -> log.debug("Found mapping from {} to {}: {}", sourceCategory, targetCategory, mapping)));
	}

	public Mono<ReferenceValueMapping> findMapping(String sourceCategory,
		String sourceContext, String sourceValue, String targetContext) {

		log.debug("Attempting to find mapping from category: {}, from context: {}, source value: {}, to context: {}",
			sourceCategory, sourceContext, sourceValue, targetContext);

		return Mono.from(repository.findOneByFromCategoryAndFromContextAndFromValueAndToContext(
				sourceCategory, sourceContext, sourceValue, targetContext))
			.doOnSuccess(consumeOnSuccess(
				() -> log.warn("No mapping found for from category: {}, from context: {}, source value: {}, to context: {}",
					sourceCategory, sourceContext, sourceValue, targetContext),
				mapping -> log.debug("Found mapping from category: {} context: {} to context {}: {}", sourceContext,
					sourceContext, targetContext, mapping)));
	}
}
