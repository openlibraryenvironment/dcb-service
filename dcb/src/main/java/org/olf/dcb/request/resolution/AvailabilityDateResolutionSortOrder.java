package org.olf.dcb.request.resolution;

import static java.util.Collections.emptyList;
import static java.util.Comparator.naturalOrder;
import static java.util.Comparator.nullsLast;
import static org.olf.dcb.utils.PropertyAccessUtils.getValue;

import java.util.Comparator;
import java.util.List;

import org.olf.dcb.core.model.Item;

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
	public Mono<List<Item>> sortItems(Parameters parameters) {
		log.debug("sortItems({})", parameters);

		return Flux.fromIterable(getValue(parameters, Parameters::getItems, emptyList()))
			.sort(Comparator.comparing(Item::getAvailableDate, nullsLast(naturalOrder())))
			.collectList();
	}
}
