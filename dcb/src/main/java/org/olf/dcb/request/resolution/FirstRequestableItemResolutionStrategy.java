package org.olf.dcb.request.resolution;

import java.util.List;
import java.util.UUID;

import org.olf.dcb.core.model.Item;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FirstRequestableItemResolutionStrategy implements ResolutionStrategy {
	private static final Logger log = LoggerFactory.getLogger(FirstRequestableItemResolutionStrategy.class);

	@Override
	public Item chooseItem(List<Item> items, UUID clusterRecordId) {
		log.debug("chooseItem({})", items);

		return items.stream()
			.filter(Item::getIsRequestable)
			.filter(Item::hasNoHolds)
			.findFirst()
			.orElseThrow(() -> new NoItemsRequestableAtAnyAgency(clusterRecordId));
	}
}
