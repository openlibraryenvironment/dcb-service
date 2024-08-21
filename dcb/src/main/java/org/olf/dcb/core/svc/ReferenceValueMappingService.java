package org.olf.dcb.core.svc;

import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.olf.dcb.core.model.ReferenceValueMapping;
import org.olf.dcb.storage.ReferenceValueMappingRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

import static services.k_int.utils.ReactorUtils.consumeOnSuccess;

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
				() -> log.warn("No mapping(1) found for from category: {}, from context: {}, source value: {}, to category: {}, to context: {}",
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
				() -> log.warn("No mapping(2) found for from category: {}, from context: {}, source value: {}, to context: {}",
					sourceCategory, sourceContext, sourceValue, targetContext),
				mapping -> log.debug("Found mapping from category: {} context: {} to context {}: {}", sourceContext,
					sourceContext, targetContext, mapping)));
	}

	/**
	 * This method exists to make configuring DCB easier by allowing an implementer to configure a set of "Default" mappings.
	 * Confiurations can now specify a "Default" or "Base" mapping - e.g "DCB" "ItemType" to "MOBUIS" "ItemType". This method will
	 * check the MOST specific context first, and then work through the list of contexts until a mapping is found or No mapping is found.
	 * This allows us to specify overrides at the level of the institution, but use default mappings for entire consortia and
	 * prevents duplicating mappings for each agency/system when common mappings will suffice
	 */
	public Mono<ReferenceValueMapping> findMappingUsingHierarchy(
	 	String sourceCategory,
		String sourceContext,
		String sourceValue,
		String targetCategory,
		List<String> targetContexts) {

		Flux<String> contexts = Flux.fromIterable(targetContexts);

		return contexts
			.doOnNext(ctx -> log.debug("Check targetContext {}",ctx) )
			.concatMap( ctx -> findMapping(sourceCategory,sourceContext,sourceValue,targetCategory,ctx))
			.doOnNext(rvm -> log.debug("result {}",rvm) )
			.next();

	}

	public Mono<ReferenceValueMapping> findMappingUsingHierarchy(
		String sourceCategory,
		List<String> sourceContexts,
		String sourceValue,
		String targetCategory,
		String targetContext) {

		Flux<String> contexts = Flux.fromIterable(sourceContexts);

		return contexts
			.doOnNext(ctx -> log.debug("Check sourceContext {}",ctx) )
			.concatMap( ctx -> findMapping(sourceCategory,ctx,sourceValue,targetCategory,targetContext))
			.doOnNext(rvm -> log.debug("result {}",rvm) )
			.next();

	}
	 	
}
