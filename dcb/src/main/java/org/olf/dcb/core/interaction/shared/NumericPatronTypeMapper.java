package org.olf.dcb.core.interaction.shared;

import static io.micronaut.core.util.StringUtils.isEmpty;

import org.olf.dcb.core.svc.HostLmsContextService;
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
	private final HostLmsContextService hostLmsContextService;

	public NumericPatronTypeMapper(NumericRangeMappingRepository numericRangeMappingRepository,
		HostLmsContextService hostLmsContextService) {
		this.numericRangeMappingRepository = numericRangeMappingRepository;
		this.hostLmsContextService = hostLmsContextService;
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
	 * The contexts to search for a patron type mapping.
	 * <p>
	 * Delegates to the one reader rather than keeping a private copy, so a Host LMS
	 * configured with a context hierarchy resolves patron types through the same
	 * contexts as its locations. The two used to be read separately and could
	 * disagree.
	 * <p>
	 * The DCB guard stays here: HostLmsContextService treats DCB as a legitimate
	 * context because it is the target mappings resolve into, but a patron whose
	 * <em>local</em> system is DCB is a caller error and must not be quietly mapped.
	 */
	private Mono<List<String>> getContextHierarchyFor(String context) {
		if ("DCB".equals(context)) {
			return Mono.error(new UnableToConvertLocalPatronTypeException("DCB used as a local context"));
		}

		return hostLmsContextService.contextHierarchyFor(context);
	}
}
