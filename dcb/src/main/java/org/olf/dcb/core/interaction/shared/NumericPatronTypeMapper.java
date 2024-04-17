package org.olf.dcb.core.interaction.shared;

import static io.micronaut.core.util.StringUtils.isEmpty;

import org.olf.dcb.storage.NumericRangeMappingRepository;

import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Singleton
public class NumericPatronTypeMapper {
	private final NumericRangeMappingRepository numericRangeMappingRepository;

	public NumericPatronTypeMapper(NumericRangeMappingRepository numericRangeMappingRepository) {
		this.numericRangeMappingRepository = numericRangeMappingRepository;
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

			return findMapping(localSystemCode, numericPatronType)
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

	private Mono<String> findMapping(String system, Long patronType) {
		return Mono.from(numericRangeMappingRepository.findMappedValueFor(
			system, "patronType", "DCB", patronType));
	}
}
