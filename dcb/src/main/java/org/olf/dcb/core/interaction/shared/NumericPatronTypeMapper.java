package org.olf.dcb.core.interaction.shared;

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

	public Mono<String> mapLocalPatronTypeToCanonical(String system, String localPatronTypeCode, String localId) {
		log.debug("mapLocalPatronTypeToCanonical({}, {})", system, localPatronTypeCode);

		// Sierra item types are integers. They are usually mapped by a range
		// I have a feeling that creating a static cache of system->localItemType mappings will have solid performance
		// benefits
		if (localPatronTypeCode != null) {
			try {
				Long l = Long.valueOf(localPatronTypeCode);
				log.debug("Look up patron type {}", l);
				return Mono.from(numericRangeMappingRepository.findMappedValueFor(system, "patronType", "DCB", l))
					.doOnNext(nrm -> log.debug("nrm: {}", nrm))
					.switchIfEmpty(Mono.error(new NoPatronTypeMappingFoundException(
						"Unable to map patronType "+system+":"+l+" To DCB context", system,
						localPatronTypeCode)));
			} catch (Exception e) {
				return Mono.error(new UnableToConvertLocalPatronTypeException(
					"Unable to convert " + localPatronTypeCode + " into number " + e.getMessage()));
			}
		}

		log.warn("No localPatronTypeCode provided");
		return Mono.error(new RuntimeException("No localPatronTypeCode provided for range mapping"));
	}
}
