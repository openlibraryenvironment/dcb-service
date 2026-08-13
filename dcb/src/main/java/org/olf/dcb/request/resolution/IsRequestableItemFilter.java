package org.olf.dcb.request.resolution;

import java.util.function.Function;

import org.olf.dcb.core.model.Item;
import org.reactivestreams.Publisher;

import io.micronaut.core.annotation.Order;
import jakarta.inject.Singleton;
import reactor.core.publisher.Mono;

@Singleton
@Order(ItemFilter.REQUESTABLE_ORDER)
public class IsRequestableItemFilter implements ItemFilter {
	public Function<Item, Publisher<Boolean>> filterItem(ItemFilterParameters parameters) {
		return item -> Mono.just(item.getIsRequestable());
	}
}
