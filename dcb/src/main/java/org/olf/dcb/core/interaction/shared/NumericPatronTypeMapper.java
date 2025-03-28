package org.olf.dcb.core.interaction.shared;

import static io.micronaut.core.util.StringUtils.isEmpty;

import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.storage.NumericRangeMappingRepository;

import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@Slf4j
@Singleton
public class NumericPatronTypeMapper {
	private final NumericRangeMappingRepository numericRangeMappingRepository;
	private final HostLmsService hostLmsService;

	public NumericPatronTypeMapper(NumericRangeMappingRepository numericRangeMappingRepository,
		HostLmsService hostLmsService) {
		this.numericRangeMappingRepository = numericRangeMappingRepository;
		this.hostLmsService = hostLmsService;
	}

	public Mono<String> mapLocalPatronTypeToCanonical(String localSystemCode, String localPatronTypeCode, String localId) {
		log.debug("mapLocalPatronTypeToCanonical({}, {})", localSystemCode, localPatronTypeCode);

		if (isEmpty(localPatronTypeCode)) {
			log.warn("No localPatronTypeCode provided");

			return Mono.error(new UnableToConvertLocalPatronTypeException(
				"Unable to map null or empty local patron type",
				localId, localSystemCode, localPatronTypeCode));
		}

		try {
			// Sierra item types are integers. They are usually mapped by a range
			// I have a feeling that creating a static cache of system->localItemType mappings will have solid performance
			// benefits
			final var numericPatronType = Long.valueOf(localPatronTypeCode);

			log.debug("Look up patron type {}", numericPatronType);

			return findMappingUsingHierarchy(localSystemCode, numericPatronType)
				.doOnNext(mapping -> log.debug("Found mapping: {}", mapping))
				.switchIfEmpty(Mono.error(new NoPatronTypeMappingFoundException(
					"Unable to map patronType %s:%d To DCB context"
						.formatted(localSystemCode, numericPatronType), localSystemCode, localPatronTypeCode)));
		} catch (Exception e) {
			return Mono.error(new UnableToConvertLocalPatronTypeException(
				"Unable to convert " + localPatronTypeCode + " into number " + e.getMessage(),
				localId, localSystemCode, localPatronTypeCode));
		}

	}

	private Mono<String> findMappingUsingHierarchy(String localSystemCode, Long numericPatronType) {
		return getContextHierarchyFor(localSystemCode)
			.flatMapMany(Flux::fromIterable)
			.concatMap( sourceContext -> findMapping(sourceContext, numericPatronType))
			.doOnNext(nrm -> log.debug("result {}",nrm) )
			.next();
	}

	private Mono<String> findMapping(String system, Long patronType) {
		return Mono.from(numericRangeMappingRepository.findMappedValueFor(system, "patronType", "DCB", patronType));
	}

	/**
	 * A way to fetch a context hierarchy for a given context.
	 */
	private Mono<List<String>> getContextHierarchyFor(String context) {

		// guard clause for non-hostlms contexts
		if ("DCB".equals(context)) {
			return Mono.error(new UnableToConvertLocalPatronTypeException("DCB used as a local context"));
		}

		return hostLmsService.getClientFor(context)
			.map(hostLmsClient -> (List<String>) hostLmsClient.getConfig().get("contextHierarchy"))
			// filter out non-null & non-empty lists
			.filter(list -> list != null && !list.isEmpty())
			// Fallback for non-null & non-empty lists
			.switchIfEmpty(Mono.defer(() -> {
				log.debug("[CONTEXT-HIERARCHY-EMPTY] " +
					"- Fetching 'contextHierarchy' returned an EMPTY list for context: '{}'", context);
				return Mono.just(List.of(context));
			}))
			// Fallback for error
			.onErrorResume(error -> {
				log.debug("[CONTEXT-HIERARCHY-ERROR] " +
					"- An ERROR occurred while fetching 'contextHierarchy' for context: '{}'.", context, error);
				return Mono.just(List.of(context));
			});
	}
}
