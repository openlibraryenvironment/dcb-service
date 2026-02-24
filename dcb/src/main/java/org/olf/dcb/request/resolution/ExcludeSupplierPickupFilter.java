package org.olf.dcb.request.resolution;

import static org.olf.dcb.core.model.FunctionalSettingType.OWN_LIBRARY_BORROWING;
import static org.olf.dcb.utils.PropertyAccessUtils.getValueOrNull;

import java.util.function.Function;

import org.olf.dcb.core.ConsortiumService;
import org.olf.dcb.core.interaction.shared.MissingParameterException;
import org.olf.dcb.core.model.Item;

import jakarta.inject.Singleton;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

@Singleton
public class ExcludeSupplierPickupFilter implements ItemFilter {
	private final ConsortiumService consortiumService;

	public ExcludeSupplierPickupFilter(ConsortiumService consortiumService) {
		this.consortiumService = consortiumService;
	}

	@Override
	public Function<Item, Publisher<Boolean>> predicate(ItemFilterParameters parameters) {
		return item -> excludeItemSupplierPickup(item, parameters);
	}

	private Mono<Boolean> excludeItemSupplierPickup(Item item, ItemFilterParameters parameters) {
		final String pickupAgencyCode = getValueOrNull(parameters, ItemFilterParameters::pickupAgencyCode);
		final String supplyingAgencyCode = getValueOrNull(item, Item::getAgencyCode);
		final String borrowingAgencyCode = getValueOrNull(parameters, ItemFilterParameters::borrowingAgencyCode);
		final Boolean isExpeditedCheckout = getValueOrNull(parameters, ItemFilterParameters::isExpeditedCheckout);

		if (pickupAgencyCode == null) {
			return Mono.error(new MissingParameterException("pickupAgencyCode"));
		}

		// Not sure what to do if supplying agency code is null
		if (supplyingAgencyCode == null) {
			return Mono.just(true);
		}

		// If it's an expedited checkout request, we must not exclude
		// Otherwise we'll break walk-up
		if (Boolean.TRUE.equals(isExpeditedCheckout)) {
			return Mono.just(true);
		}

		// And if pickup and supplier aren't the same we're also not bothered
		if (!pickupAgencyCode.equals(supplyingAgencyCode)) {
			return Mono.just(true);
		}

		boolean isLocalRequest = supplyingAgencyCode.equals(borrowingAgencyCode);

		if (isLocalRequest) {
			// If it's a local request, check if own library borrowing is switched on
			// If it is, do not exclude.
			// If it's not, exclude.
			return consortiumService.isEnabled(OWN_LIBRARY_BORROWING)
					.map(enabled -> enabled);
		}

		// End solution: this is a supplier pickup request that isn't walk-up or local. So exclude.
		return Mono.just(false);
	}
}
