package org.olf.dcb.core.interaction.shared;

import org.olf.dcb.storage.NumericRangeMappingRepository;

import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Singleton
public class NumericItemTypeMapper {
	private final NumericRangeMappingRepository numericRangeMappingRepository;

	public NumericItemTypeMapper(NumericRangeMappingRepository numericRangeMappingRepository) {
		this.numericRangeMappingRepository = numericRangeMappingRepository;
	}

	// Sierra item type comes from fixed field 61 - see https://documentation.iii.com/sierrahelp/Content/sril/sril_records_fixed_field_types_item.html
	public Mono<String> getCanonicalItemType(String system, String localItemTypeCode) {
		log.debug("getCanonicalItemType({}, {})", system, localItemTypeCode);

		if (localItemTypeCode == null) {
			log.warn("No localItemType provided - returning UNKNOWN");
			return Mono.just("UNKNOWN");
		}

		// Sierra item types are integers. They are usually mapped by a range
		// I have a feeling that creating a static cache of system->localItemType mappings will have solid performance
		// benefits
		try {
			Long l = Long.valueOf(localItemTypeCode);
			log.debug("Look up item type {}", l);
			return Mono.from(numericRangeMappingRepository.findMappedValueFor(system, "ItemType", "DCB", l))
				.doOnNext(nrm -> log.debug("nrm: {}", nrm))
				.defaultIfEmpty("UNKNOWN");
		} catch (Exception e) {
			log.warn("Problem trying to convert {} into  long value", localItemTypeCode);
			return Mono.just("UNKNOWN");
		}
	}
}
