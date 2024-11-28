package org.olf.dcb.request.resolution;

import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.olf.dcb.core.model.Item;
import org.olf.dcb.core.model.PatronRequest;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Slf4j
@Singleton
public class AvailabilityDateResolutionSortOrder implements ResolutionSortOrder {

	@Override
	public String getCode() {
		return CODE_AVAILABILITY_DATE;
	}

	@Override
	public Mono<List<Item>> sortItems(List<Item> items, UUID clusterRecordId, PatronRequest patronRequest) {
		log.debug("sortItems(array of size {},{},{})", items.size(), clusterRecordId, patronRequest);

		return Flux.fromIterable(items)
			.map(item -> item.setAvailableDate(Instant.now()))
			.sort(Comparator.comparing(Item::getAvailableDate))
			.collectList();
	}
}
