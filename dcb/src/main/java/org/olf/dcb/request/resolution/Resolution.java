package org.olf.dcb.request.resolution;

import static java.util.Collections.emptyList;
import static lombok.AccessLevel.PRIVATE;
import static org.olf.dcb.utils.PropertyAccessUtils.getValueOrNull;

import java.util.List;
import java.util.UUID;

import org.olf.dcb.core.model.Item;
import org.olf.dcb.core.model.PatronRequest;

import lombok.Builder;
import lombok.Value;

@Builder(access = PRIVATE)
@Value
public class Resolution implements ItemFilterParameters {
	PatronRequest patronRequest;
	@Builder.Default List<String> excludedAgencyCodes = emptyList();
	String borrowingAgencyCode;

	Item chosenItem;

	@Builder.Default List<Item> allItems = emptyList();
	@Builder.Default List<Item> filteredItems = emptyList();
	@Builder.Default List<Item> sortedItems = emptyList();

	public static Resolution forPatronRequest(PatronRequest patronRequest) {
		return builder().patronRequest(patronRequest).build();
	}

	public boolean successful() {
		return getChosenItem() != null;
	}

	public Resolution withPatronRequest(PatronRequest newPatronRequest) {
		return builder()
			.patronRequest(newPatronRequest)
			.excludedAgencyCodes(excludedAgencyCodes)
			.borrowingAgencyCode(borrowingAgencyCode)
			.chosenItem(chosenItem)
			.allItems(allItems)
			.filteredItems(filteredItems)
			.sortedItems(sortedItems)
			.chosenItem(chosenItem)
			.build();
	}

	public UUID getBibClusterId() {
		return getValueOrNull(patronRequest, PatronRequest::getBibClusterId);
	}

	public Resolution excludeAgencies(List<String> excludedAgencyCodes) {
		return builder()
			.patronRequest(patronRequest)
			.excludedAgencyCodes(excludedAgencyCodes)
			.borrowingAgencyCode(borrowingAgencyCode)
			.allItems(allItems)
			.filteredItems(filteredItems)
			.sortedItems(sortedItems)
			.chosenItem(chosenItem)
			.build();
	}

	public Resolution borrowingAgency(String agencyCode) {
		return builder()
			.patronRequest(patronRequest)
			.excludedAgencyCodes(excludedAgencyCodes)
			.borrowingAgencyCode(agencyCode)
			.allItems(allItems)
			.filteredItems(filteredItems)
			.sortedItems(sortedItems)
			.chosenItem(chosenItem)
			.build();
	}

	public Resolution trackAllItems(List<Item> allItems) {
		return builder()
			.patronRequest(patronRequest)
			.excludedAgencyCodes(excludedAgencyCodes)
			.borrowingAgencyCode(borrowingAgencyCode)
			.allItems(allItems)
			.filteredItems(filteredItems)
			.sortedItems(sortedItems)
			.chosenItem(chosenItem)
			.build();
	}

	public Resolution trackFilteredItems(List<Item> filteredItems) {
		return Resolution.builder()
			.patronRequest(patronRequest)
			.excludedAgencyCodes(excludedAgencyCodes)
			.borrowingAgencyCode(borrowingAgencyCode)
			.allItems(allItems)
			.filteredItems(filteredItems)
			.sortedItems(sortedItems)
			.chosenItem(chosenItem)
			.build();
	}

	public Resolution trackSortedItems(List<Item> sortedItems) {
		return Resolution.builder()
			.patronRequest(patronRequest)
			.excludedAgencyCodes(excludedAgencyCodes)
			.borrowingAgencyCode(borrowingAgencyCode)
			.allItems(allItems)
			.filteredItems(filteredItems)
			.sortedItems(sortedItems)
			.chosenItem(chosenItem)
			.build();
	}

	public Resolution selectItem(Item item) {
		return Resolution.builder()
			.patronRequest(patronRequest)
			.excludedAgencyCodes(excludedAgencyCodes)
			.borrowingAgencyCode(borrowingAgencyCode)
			.allItems(allItems)
			.filteredItems(filteredItems)
			.sortedItems(sortedItems)
			.chosenItem(item)
			.build();
	}

	static Resolution noItemsSelectable(PatronRequest patronRequest) {
		return builder()
			.patronRequest(patronRequest)
			.build();
	}
}
