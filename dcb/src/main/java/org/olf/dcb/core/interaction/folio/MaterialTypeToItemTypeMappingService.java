package org.olf.dcb.core.interaction.folio;

import static io.micronaut.core.util.StringUtils.isEmpty;
import static org.olf.dcb.core.interaction.shared.NumericItemTypeMapper.*;
import static org.olf.dcb.utils.PropertyAccessUtils.getValueOrNull;

import org.olf.dcb.core.model.Item;
import org.olf.dcb.core.model.ReferenceValueMapping;
import org.olf.dcb.core.svc.ReferenceValueMappingService;

import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Singleton
public class MaterialTypeToItemTypeMappingService {
	private final ReferenceValueMappingService referenceValueMappingService;

	public MaterialTypeToItemTypeMappingService(
		ReferenceValueMappingService referenceValueMappingService) {

		this.referenceValueMappingService = referenceValueMappingService;
	}

	public Mono<Item> enrichItemWithMappedItemType(Item item) {

		final var hostLmsCode = getValueOrNull(item, Item::getOwningContext);

		return findMapping(item, hostLmsCode)
			.map(item::setCanonicalItemType);
	}

	private Mono<String> findMapping(Item item, String hostLmsCode) {

		final var localItemTypeCode = getValueOrNull(item, Item::getLocalItemTypeCode);

		if (hostLmsCode == null) {
			log.warn("No hostLmsCode provided - returning {} for {} - {}", UNKNOWN_NULL_HOSTLMSCODE, localItemTypeCode, item);
			return Mono.just(UNKNOWN_NULL_HOSTLMSCODE);
		}

		if (isEmpty(localItemTypeCode)) {
			log.warn("Attempting to map null / empty local item type code for Host LMS: {} - {}", hostLmsCode, item);
			return Mono.just(UNKNOWN_NULL_LOCAL_ITEM_TYPE);
		}

		return referenceValueMappingService.findMapping("ItemType", hostLmsCode,
				localItemTypeCode, "ItemType", "DCB")
			.map(ReferenceValueMapping::getToValue)
			.defaultIfEmpty(UNKNOWN_NO_MAPPING_FOUND);
	}
}
