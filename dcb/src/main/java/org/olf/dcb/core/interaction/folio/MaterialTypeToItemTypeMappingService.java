package org.olf.dcb.core.interaction.folio;

import org.olf.dcb.core.model.Item;
import org.olf.dcb.core.model.ReferenceValueMapping;
import org.olf.dcb.core.svc.ReferenceValueMappingService;

import jakarta.inject.Singleton;
import reactor.core.publisher.Mono;

@Singleton
public class MaterialTypeToItemTypeMappingService {
	private final ReferenceValueMappingService referenceValueMappingService;

	public MaterialTypeToItemTypeMappingService(
		ReferenceValueMappingService referenceValueMappingService) {

		this.referenceValueMappingService = referenceValueMappingService;
	}

	public Mono<Item> enrichItemWithMappedItemType(Item item, String hostLmsCode) {
		return referenceValueMappingService.findMapping("ItemType", hostLmsCode,
				item.getLocalItemTypeCode(), "ItemType", "DCB")
			.map(ReferenceValueMapping::getToValue)
			.map(item::setCanonicalItemType);
	}
}
