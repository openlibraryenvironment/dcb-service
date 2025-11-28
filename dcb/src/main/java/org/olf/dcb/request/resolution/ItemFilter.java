package org.olf.dcb.request.resolution;

import java.util.function.Function;

import org.olf.dcb.core.model.Item;
import org.reactivestreams.Publisher;

public interface ItemFilter {
	Function<Item, Publisher<Boolean>> predicate(ItemFilterParameters parameters);
}
