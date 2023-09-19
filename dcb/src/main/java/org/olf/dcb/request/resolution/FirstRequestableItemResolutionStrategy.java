package org.olf.dcb.request.resolution;

import java.util.List;
import java.util.UUID;

import org.olf.dcb.core.model.Item;
import org.olf.dcb.core.model.PatronRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.inject.Singleton;

import reactor.core.publisher.Mono;

@Singleton
public class FirstRequestableItemResolutionStrategy implements ResolutionStrategy {
	private static final Logger log = LoggerFactory.getLogger(FirstRequestableItemResolutionStrategy.class);

	public static final String FIRST_ITEM_STRATEGY = "FirstItem";

	@Override
	public String getCode() {
		return FIRST_ITEM_STRATEGY;
	}


	@Override
	public Mono<Item> chooseItem(List<Item> items, UUID clusterRecordId, PatronRequest patronRequest) {
		log.debug("chooseItem(array of size {})", items.size());

		return Mono.just(
			items.stream()
                       		.peek(item -> log.debug("Consider item requestable:{} holds:{} ",item.getIsRequestable(),item.hasNoHolds()))
				.filter(Item::getIsRequestable)
				.filter(Item::hasNoHolds)
				.findFirst()
				.orElseThrow(() -> new NoItemsRequestableAtAnyAgency(clusterRecordId))
		);
	}
}
