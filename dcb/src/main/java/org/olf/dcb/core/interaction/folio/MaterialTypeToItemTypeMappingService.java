package org.olf.dcb.core.interaction.folio;

import static io.micronaut.core.util.StringUtils.isEmpty;
import static org.olf.dcb.utils.PropertyAccessUtils.getValue;

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

	public Mono<Item> enrichItemWithMappedItemType(Item item, String hostLmsCode) {
		return findMapping(item, hostLmsCode)
			.map(item::setCanonicalItemType);
	}

	private Mono<String> findMapping(Item item, String hostLmsCode) {
		final var unknownItemType = "UNKNOWN";

		final var localItemTypeCode = getValue(item, Item::getLocalItemTypeCode);

		if (isEmpty(localItemTypeCode)) {
			log.warn("Attempting to map null / empty local item type code for Host LMS: {}", hostLmsCode);

			return Mono.just(unknownItemType);
		}

		return referenceValueMappingService.findMapping("ItemType", hostLmsCode,
				localItemTypeCode, "ItemType", "DCB")
			.map(ReferenceValueMapping::getToValue)
			.defaultIfEmpty(unknownItemType);
	}
}
