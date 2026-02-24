package org.olf.dcb.request.resolution;

import static org.olf.dcb.core.model.FunctionalSettingType.SELECT_UNAVAILABLE_ITEMS;

import java.util.function.Function;

import org.olf.dcb.core.ConsortiumService;
import org.olf.dcb.core.model.Item;
import org.reactivestreams.Publisher;

import jakarta.inject.Singleton;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@AllArgsConstructor
public class IncludeItemWithHoldsItemFilter implements ItemFilter {
	private final ConsortiumService consortiumService;

	/**
	 * Checks if an item with holds should be included in the resolution process.
	 * <p>
	 * This method checks the consortium's functional setting for selecting unavailable items.
	 * If the setting is enabled, items with holds are included. Otherwise, only items with no holds are included.
	 */
	public Function<Item, Publisher<Boolean>> filterItem(ItemFilterParameters parameters) {
		return item -> consortiumService.isEnabled(SELECT_UNAVAILABLE_ITEMS)
			.map(enabled -> {
				final boolean includeItem = enabled || item.hasNoHolds();

				log.debug(
					"Include item with holds: enabled={}, item.hasNoHolds={}, includeItem={}",
					enabled, item.hasNoHolds(), includeItem);

				return includeItem;
			});
	}
}
