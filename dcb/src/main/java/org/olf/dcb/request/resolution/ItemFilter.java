package org.olf.dcb.request.resolution;

import java.util.function.Function;

import org.olf.dcb.core.model.Item;
import org.reactivestreams.Publisher;

/**
 * Decides whether an item may supply a request.
 * <p>
 * Implementations are composed by {@link AllItemFilters} in {@link io.micronaut.core.annotation.Order}
 * order, cheapest first, so that an item rejected on a plain field comparison never
 * reaches a filter that would go to the database or build a Host LMS client for it.
 * <p>
 * <strong>An implementation must be a total predicate.</strong> Raising from here
 * aborts resolution for the whole cluster record rather than excluding one item, and
 * because the composite short-circuits, whether that happens at all depends on where
 * the filter sits in the order. Anything an implementation cannot evaluate is a
 * <code>false</code> and a log line.
 */
public interface ItemFilter {
	int REQUESTABLE_ORDER = 10;
	int AGENCY_EXCLUSION_ORDER = 20;
	int SAME_AGENCY_ORDER = 30;
	int SUPPLIER_PICKUP_ORDER = 40;
	int ITEM_WITH_HOLDS_ORDER = 50;
	int SAME_SERVER_ORDER = 60;

	Function<Item, Publisher<Boolean>> filterItem(ItemFilterParameters parameters);
}
