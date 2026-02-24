package org.olf.dcb.request.resolution;

import java.util.function.Function;

import org.olf.dcb.core.model.Item;
import org.reactivestreams.Publisher;

import jakarta.inject.Singleton;
import reactor.core.publisher.Mono;

@Singleton
public class IsRequestableItemFilter implements ItemFilter {
	public Function<Item, Publisher<Boolean>> filterItem(ItemFilterParameters parameters) {
		return item -> Mono.just(item.getIsRequestable());
	}
}
