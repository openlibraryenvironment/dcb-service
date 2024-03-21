package org.olf.dcb.request.resolution;

import static org.olf.dcb.utils.PropertyAccessUtils.getValue;

import java.util.List;
import java.util.UUID;

import org.olf.dcb.core.model.Item;
import org.olf.dcb.core.model.PatronRequest;

import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Singleton
public class FirstRequestableItemResolutionStrategy implements ResolutionStrategy {
	@Override
	public String getCode() {
		return "FirstItem";
	}
	
	@Override
	public Mono<Item> chooseItem(List<Item> items, UUID clusterRecordId, PatronRequest patronRequest) {
		final var patronRequestId = getValue(patronRequest, PatronRequest::getId);

		log.info("PR-{} - chooseItem(array of size {})", patronRequestId, items.size());

		return Mono.just(
			items.stream()
				.peek(item -> log.info(
						"PR-{} - Consider item {} @ {} requestable:{} holds:{} ",
					patronRequestId,
					item.getLocalId(),
					item.getLocation(),
					item.getIsRequestable(),
					item.hasNoHolds()))
				.filter(Item::getIsRequestable)
				.filter(Item::hasNoHolds)
				.findFirst()
				.orElseThrow(() -> new NoItemsRequestableAtAnyAgency(clusterRecordId))
		);
	}
}
