package org.olf.dcb.request.resolution;

import java.util.List;
import java.util.UUID;

import org.olf.dcb.core.model.Item;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.inject.Singleton;

@Singleton
public class GeoDistanceResolutionStrategy implements ResolutionStrategy {
	private static final Logger log = LoggerFactory.getLogger(GeoDistanceResolutionStrategy.class);

	public static final String GEO_DISTANCE_STRATEGY = "Geo";

	@Override
	public String getCode() {
		return GEO_DISTANCE_STRATEGY;
	}


	@Override
	public Item chooseItem(List<Item> items, UUID clusterRecordId) {
		log.debug("chooseItem(array of size {})", items.size());

		return items.stream()
			.filter(Item::getIsRequestable)
			.filter(Item::hasNoHolds)
			.findFirst()
			.orElseThrow(() -> new NoItemsRequestableAtAnyAgency(clusterRecordId));
	}
}
