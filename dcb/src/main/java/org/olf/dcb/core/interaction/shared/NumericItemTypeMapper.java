package org.olf.dcb.core.interaction.shared;

import org.olf.dcb.core.model.Item;
import org.olf.dcb.storage.NumericRangeMappingRepository;

import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import static org.olf.dcb.utils.PropertyAccessUtils.getValueOrNull;

@Slf4j
@Singleton
public class NumericItemTypeMapper {
	private final NumericRangeMappingRepository numericRangeMappingRepository;

	// Static constants for failure reasons
	public static final String UNKNOWN_NULL_LOCAL_ITEM_TYPE = "UNKNOWN - NULL localItemTypeCode";
	public static final String UNKNOWN_NULL_HOSTLMSCODE = "UNKNOWN - NULL hostLmsCode";
	public static final String UNKNOWN_INVALID_LOCAL_ITEM_TYPE = "UNKNOWN - Invalid localItemTypeCode";
	public static final String UNKNOWN_NO_MAPPING_FOUND = "UNKNOWN - No mapping found";
	public static final String UNKNOWN_UNEXPECTED_FAILURE = "UNKNOWN - Unexpected failure";
	public static final String MISSING_OWNING_CONTEXT = "UNKNOWN - Item has no owning context";

	public NumericItemTypeMapper(NumericRangeMappingRepository numericRangeMappingRepository) {
		this.numericRangeMappingRepository = numericRangeMappingRepository;
	}

	public Mono<org.olf.dcb.core.model.Item> enrichItemWithMappedItemType(org.olf.dcb.core.model.Item item) {

		final var hostLmsCode = getValueOrNull(item, Item::getOwningContext);

		if ( hostLmsCode == null )
			return Mono.just(item.setCanonicalItemType(MISSING_OWNING_CONTEXT));

		return getCanonicalItemType(hostLmsCode, item.getLocalItemTypeCode())
			.defaultIfEmpty(UNKNOWN_UNEXPECTED_FAILURE)
			.map(item::setCanonicalItemType);
	}

	private Mono<String> getCanonicalItemType(String system, String localItemTypeCode) {
		log.debug("getCanonicalItemType({}, {})", system, localItemTypeCode);

		if (system == null) {
			log.warn("No hostLmsCode provided - returning {}", UNKNOWN_NULL_HOSTLMSCODE);
			return Mono.just(UNKNOWN_NULL_HOSTLMSCODE);
		}

		if (localItemTypeCode == null) {
			log.warn("No localItemType provided - returning {}", UNKNOWN_NULL_LOCAL_ITEM_TYPE);
			return Mono.just(UNKNOWN_NULL_LOCAL_ITEM_TYPE);
		}

		try {
			Long l = Long.valueOf(localItemTypeCode);
			log.debug("Look up item type {}", l);
			return Mono.from(numericRangeMappingRepository.findMappedValueFor(system, "ItemType", "DCB", l))
				.doOnNext(nrm -> log.debug("nrm: {}", nrm))
				.defaultIfEmpty(UNKNOWN_NO_MAPPING_FOUND);
		} catch (Exception e) {
			log.warn("Problem trying to convert {} into long value - returning {}",
				localItemTypeCode, UNKNOWN_INVALID_LOCAL_ITEM_TYPE);
			return Mono.just(UNKNOWN_INVALID_LOCAL_ITEM_TYPE);
		}
	}
}
