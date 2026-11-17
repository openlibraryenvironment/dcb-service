package org.olf.dcb.request.resolution;

import static org.olf.dcb.core.model.DerivedLoanPolicy.LOCAL_ONLY;

import java.util.Objects;
import java.util.function.Function;

import org.olf.dcb.core.model.Item;
import org.olf.dcb.item.availability.RequestableItemService;
import org.reactivestreams.Publisher;

import io.micronaut.core.annotation.Order;
import jakarta.inject.Singleton;
import reactor.core.publisher.Mono;

@Singleton
@Order(ItemFilter.REQUESTABLE_ORDER)
public class IsRequestableItemFilter implements ItemFilter {
	private final RequestableItemService requestableItemService;

	public IsRequestableItemFilter(RequestableItemService requestableItemService) {
		this.requestableItemService = requestableItemService;
	}

	public Function<Item, Publisher<Boolean>> filterItem(ItemFilterParameters parameters) {
		return item -> {
			if (Boolean.TRUE.equals(item.getIsRequestable())) {
				return Mono.just(true);
			}

			// A LOCAL_ONLY item reads not requestable to everyone, so a borrower on its own system asks again
			return LOCAL_ONLY == item.getDerivedLoanPolicy() && isOnItsSystem(item, parameters)
				? requestableItemService.isRequestableWithinItsSystem(item)
				: Mono.just(false);
		};
	}

	private static boolean isOnItsSystem(Item item, ItemFilterParameters parameters) {
		return item.getHostLmsCode() != null && parameters != null
			&& Objects.equals(item.getHostLmsCode(), parameters.borrowingHostLmsCode());
	}
}
