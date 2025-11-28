package org.olf.dcb.request.resolution;

import static org.olf.dcb.core.model.FunctionalSettingType.OWN_LIBRARY_BORROWING;
import static org.olf.dcb.utils.PropertyAccessUtils.getValueOrNull;

import java.util.function.Function;

import org.olf.dcb.core.ConsortiumService;
import org.olf.dcb.core.model.Item;
import org.reactivestreams.Publisher;

import reactor.core.publisher.Mono;

public class ExcludeFromSameAgencyItemFilter {
	private final ConsortiumService consortiumService;

	public ExcludeFromSameAgencyItemFilter(ConsortiumService consortiumService) {
		this.consortiumService = consortiumService;
	}

	public Function<Item, Publisher<Boolean>> predicate(ItemFilterParameters parameters) {
		return item -> excludeItemFromSameAgency(item, parameters);
	}

	private Mono<Boolean> excludeItemFromSameAgency(Item item, ItemFilterParameters parameters) {
		final var borrowingAgencyCode = getValueOrNull(parameters,
			ItemFilterParameters::getBorrowingAgencyCode);

		return consortiumService.isEnabled(OWN_LIBRARY_BORROWING)
			.map(enabled -> {
				if (enabled) {
					return true;
				}

				final var itemAgencyCode = getValueOrNull(item, Item::getAgencyCode);

				return itemAgencyCode != null && !itemAgencyCode.equals(borrowingAgencyCode);
			});
	}
}
