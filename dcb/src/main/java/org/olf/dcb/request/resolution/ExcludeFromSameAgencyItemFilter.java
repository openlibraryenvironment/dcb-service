package org.olf.dcb.request.resolution;

import static org.olf.dcb.core.model.FunctionalSettingType.OWN_LIBRARY_BORROWING;
import static org.olf.dcb.utils.PropertyAccessUtils.getValueOrNull;

import java.util.function.Function;

import org.olf.dcb.core.ConsortiumService;
import org.olf.dcb.core.interaction.shared.MissingParameterException;
import org.olf.dcb.core.model.Item;
import org.reactivestreams.Publisher;

import jakarta.inject.Singleton;
import reactor.core.publisher.Mono;

@Singleton
public class ExcludeFromSameAgencyItemFilter implements ItemFilter {
	private final ConsortiumService consortiumService;

	public ExcludeFromSameAgencyItemFilter(ConsortiumService consortiumService) {
		this.consortiumService = consortiumService;
	}

	@Override
	public Function<Item, Publisher<Boolean>> predicate(ItemFilterParameters parameters) {
		return item -> excludeItemFromSameAgency(item, parameters);
	}

	private Mono<Boolean> excludeItemFromSameAgency(Item item, ItemFilterParameters parameters) {
		final var borrowingAgencyCode = getValueOrNull(parameters,
			ItemFilterParameters::borrowingAgencyCode);

		if (borrowingAgencyCode == null) {
			return Mono.error(new MissingParameterException("borrowingAgencyCode"));
		}

		final var pickupAgencyCode = getValueOrNull(parameters,
			ItemFilterParameters::pickupAgencyCode);

		if (pickupAgencyCode == null) {
			return Mono.error(new MissingParameterException("pickupAgencyCode"));
		}

		return consortiumService.isEnabled(OWN_LIBRARY_BORROWING)
			.map(enabled -> {
				if (enabled) {
					return pickupAgencyCode.equals(borrowingAgencyCode);
				}

				final var itemAgencyCode = getValueOrNull(item, Item::getAgencyCode);

				return itemAgencyCode != null && !itemAgencyCode.equals(borrowingAgencyCode);
			});
	}
}
