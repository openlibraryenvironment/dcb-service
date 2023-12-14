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

	public Mono<ReferenceValueMapping> findMapping(String fromCategory,
		String fromContext, String sourceValue, String toCategory, String toContext) {

		log.debug("Attempting to find mapping from category: {}, from context: {}, source value: {}, to category: {}, to context: {}",
			fromCategory, fromContext, sourceValue, toCategory, toContext);

		return Mono.from(repository.findOneByFromCategoryAndFromContextAndFromValueAndToCategoryAndToContext(
				fromCategory, fromContext, sourceValue, toCategory, toContext))
			.doOnSuccess(consumeOnSuccess(
				() -> log.warn("No mapping found for from category: {}, from context: {}, source value: {}, to category: {}, to context: {}",
					fromCategory, fromContext, sourceValue, toCategory, toContext),
				mapping -> log.debug("Found mapping from {} to {}: {}", fromCategory, toCategory, mapping)));
	}

	public Mono<ReferenceValueMapping> findMapping(String sourceCategory,
		String sourceContext, String sourceValue, String targetContext) {

		log.debug("findMapping targetCtx={} sourceCtx={} value={}",targetContext,sourceContext,sourceValue);

		return Mono.from(repository.findOneByFromCategoryAndFromContextAndFromValueAndToContext(
			sourceCategory, sourceContext, sourceValue, targetContext));
	}
}
