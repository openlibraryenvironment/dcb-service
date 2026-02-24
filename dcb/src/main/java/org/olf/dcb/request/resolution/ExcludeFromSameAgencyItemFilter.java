package org.olf.dcb.request.resolution;

import static org.olf.dcb.core.model.FunctionalSettingType.OWN_LIBRARY_BORROWING;
import static org.olf.dcb.utils.PropertyAccessUtils.getValueOrNull;

import java.util.function.Function;

import org.olf.dcb.core.ConsortiumService;
import org.olf.dcb.core.model.Item;
import org.reactivestreams.Publisher;

import jakarta.inject.Singleton;
import lombok.AllArgsConstructor;
import reactor.core.publisher.Mono;

@Singleton
@AllArgsConstructor
public class ExcludeFromSameAgencyItemFilter implements ItemFilter {
	private final ConsortiumService consortiumService;

	@Override
	public Function<Item, Publisher<Boolean>> filterItem(ItemFilterParameters parameters) {
		return item -> excludeItemFromSameAgency(item, parameters);
	}

	private Mono<Boolean> excludeItemFromSameAgency(Item item, ItemFilterParameters parameters) {
		final var borrowingAgencyCode = getValueOrNull(parameters,
			ItemFilterParameters::borrowingAgencyCode);

		final var pickupAgencyCode = getValueOrNull(parameters,
			ItemFilterParameters::pickupAgencyCode);

		return consortiumService.isEnabled(OWN_LIBRARY_BORROWING)
			.map(enabled -> {
				if (enabled) {
					// Pickup anywhere and expedited workflows
					if (isPickupElsewhere(pickupAgencyCode, borrowingAgencyCode)) {
						return itemIsFromElsewhere(item, borrowingAgencyCode);
					}

					return true;
				}

				return itemIsFromElsewhere(item, borrowingAgencyCode);
			});
	}

	private static boolean isPickupElsewhere(String pickupAgencyCode, String borrowingAgencyCode) {
		return !pickupAgencyCode.equals(borrowingAgencyCode);
	}

	private static boolean itemIsFromElsewhere(Item item, String borrowingAgencyCode) {
		final var itemAgencyCode = getValueOrNull(item, Item::getAgencyCode);

		return itemAgencyCode != null && isPickupElsewhere(itemAgencyCode, borrowingAgencyCode);
	}
}
