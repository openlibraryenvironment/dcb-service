package org.olf.dcb.request.resolution;

import static java.util.Collections.emptyList;
import static org.olf.dcb.utils.PropertyAccessUtils.getValue;

import java.util.List;
import java.util.Optional;

import org.olf.dcb.core.model.Item;

class AgencyExclusionItemFilter {
	/**
	 * Checks if an item should be excluded from the resolution process because it belongs to an excluded agency.
	 * <p>
	 * This method checks if the item's agency code matches an excluded agency's code
	 * If the item's agency code matches, it is excluded
	 *
	 * @param item the item to check
	 * @param parameters parameters that could affect the filtering decision, in this case, the list of excluded agencies
	 * @return true if the item should be included in the resolution process, false otherwise
	 */
	public boolean filterItem(Item item, ItemFilterParameters parameters) {
		final List<String> excludedAgencyCodes = getValue(parameters,
			ItemFilterParameters::getExcludedSupplyingAgencyCodes, emptyList());

		// if the item is present
		return Optional.ofNullable(item)
			// and the item has an agency code
			.map(Item::getAgencyCode)
			// and the agency code is in the set of excluded agencies
			.filter(excludedAgencyCodes::contains)
			// our conditions didn't match so include the item
			.isEmpty();
	}
}
