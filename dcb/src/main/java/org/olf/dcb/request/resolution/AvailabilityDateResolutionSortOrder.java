package org.olf.dcb.request.resolution;

import static java.util.Comparator.naturalOrder;
import static java.util.Comparator.nullsLast;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.olf.dcb.core.model.Item;
import org.olf.dcb.core.model.PatronRequest;

import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

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
			.sort(Comparator.comparing(Item::getAvailableDate, nullsLast(naturalOrder())))
			.collectList();
	}
}
