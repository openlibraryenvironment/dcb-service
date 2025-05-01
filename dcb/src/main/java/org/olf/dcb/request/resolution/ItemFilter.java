package org.olf.dcb.request.resolution;

import org.olf.dcb.core.model.Item;

import reactor.core.publisher.Flux;

public interface ItemFilter {
	Flux<Item> filterItems(Flux<Item> items, ItemFilterParameters parameters);
}
