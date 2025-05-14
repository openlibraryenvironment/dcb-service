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

		log.debug("Attempting to find mapping(1) from category: {}, from context: {}, source value: {}, to category: {}, to context: {}",
			sourceCategory, sourceContext, sourceValue, targetCategory, targetContext);

		return Mono.from(repository.findOneByFromCategoryAndFromContextAndFromValueAndToCategoryAndToContext(
				sourceCategory, sourceContext, sourceValue, targetCategory, targetContext))
			.doOnSuccess(consumeOnSuccess(
				() -> log.debug("mapping(1) found for from category: {}, from context: {}, source value: {}, to category: {}, to context: {}",
					sourceCategory, sourceContext, sourceValue, targetCategory, targetContext),
				mapping -> log.debug("Found mapping from {} to {}: {}", sourceCategory, targetCategory, mapping)));
	}

	public Mono<ReferenceValueMapping> findMapping(String sourceCategory,
		String sourceContext, String sourceValue, String targetContext) {

		log.debug("Attempting to find mapping(2) from category: {}, from context: {}, source value: {}, to context: {}",
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
	 * This variant introduces the idea of fallback value. The aim is to allow callers to specify a wildcard default so that
	 * direct mapping files could have (For example) From: HostSystem:Location:* TO: DCB:AGENCY:agency0 to mapp all locations at HostSystem0 to agency0
	 * this will not be appropriate in all scenarios (Shared servers) but should simplify config for the most common case substantially.
	 * Thus we don't need to provide a specific mapping for location0 because we always try Location:* second
	 */
  public Mono<ReferenceValueMapping> findMappingUsingHierarchyWithFallback(
    String sourceCategory,
    String sourceContext,
    List<String> sourceValues,
    String targetCategory,
    List<String> targetContexts) {

    Flux<String> contexts = Flux.fromIterable(targetContexts);

    return contexts
      .concatMap( ctx ->
				Flux.fromIterable(sourceValues)
					.concatMap( sourceValue -> findMapping(sourceCategory,sourceContext,sourceValue,targetCategory,ctx))
				)
      .next();
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

  public Mono<ReferenceValueMapping> findMappingUsingHierarchyWithFallback(
    String sourceCategory,
    List<String> sourceContexts,
    List<String> sourceValues,
    String targetCategory,
    String targetContext) {

    Flux<String> contexts = Flux.fromIterable(sourceContexts);

    return contexts
			.concatMap( ctx ->
				Flux.fromIterable(sourceValues)
		      .concatMap( sourceValue -> findMapping(sourceCategory,ctx,sourceValue,targetCategory,targetContext))
				)
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
