package org.olf.dcb.request.resolution;

import lombok.Builder;
import lombok.Value;
import org.olf.dcb.core.model.Item;
import org.olf.dcb.core.model.PatronRequest;

import java.util.List;
import java.util.UUID;

import static java.util.Collections.emptyList;
import static lombok.AccessLevel.PRIVATE;
import static org.olf.dcb.utils.PropertyAccessUtils.getValueOrNull;

@Builder(access = PRIVATE)
@Value
public class Resolution {
	PatronRequest patronRequest;
	String excludedAgencyCode;
	String borrowingAgencyCode;

	Item chosenItem;

	@Builder.Default List<Item> allItems = emptyList();
	@Builder.Default List<Item> filteredItems = emptyList();
	@Builder.Default List<Item> sortedItems = emptyList();

	public static Resolution forPatronRequest(PatronRequest patronRequest) {
		return builder().patronRequest(patronRequest).build();
	}

	public Resolution withPatronRequest(PatronRequest newPatronRequest) {
		return builder()
			.patronRequest(newPatronRequest)
			.excludedAgencyCode(excludedAgencyCode)
			.borrowingAgencyCode(borrowingAgencyCode)
			.chosenItem(chosenItem)
			.allItems(allItems)
			.filteredItems(filteredItems)
			.sortedItems(sortedItems)
			.build();
	}

	public UUID getBibClusterId() {
		return getValueOrNull(patronRequest, PatronRequest::getBibClusterId);
	}

	public Resolution excludeAgency(String agencyCode) {
		return builder()
			.patronRequest(patronRequest)
			.borrowingAgencyCode(borrowingAgencyCode)
			.excludedAgencyCode(agencyCode)
			.build();
	}

	public Resolution borrowingAgency(String agencyCode) {
		return builder()
			.patronRequest(patronRequest)
			.excludedAgencyCode(excludedAgencyCode)
			.borrowingAgencyCode(agencyCode)
			.build();
	}

	public Resolution trackAllItems(List<Item> allItems) {
		return builder()
			.patronRequest(patronRequest)
			.excludedAgencyCode(excludedAgencyCode)
			.borrowingAgencyCode(borrowingAgencyCode)
			.allItems(allItems)
			.build();
	}

	public Resolution trackFilteredItems(List<Item> filteredItems) {
		return Resolution.builder()
			.patronRequest(patronRequest)
			.excludedAgencyCode(excludedAgencyCode)
			.borrowingAgencyCode(borrowingAgencyCode)
			.allItems(allItems)
			.filteredItems(filteredItems)
			.build();
	}

	public Resolution trackSortedItems(List<Item> sortedItems) {
		return Resolution.builder()
			.patronRequest(patronRequest)
			.excludedAgencyCode(excludedAgencyCode)
			.borrowingAgencyCode(borrowingAgencyCode)
			.allItems(allItems)
			.filteredItems(filteredItems)
			.sortedItems(sortedItems)
			.build();
	}

	public Resolution selectItem(Item item) {
		return Resolution.builder()
			.patronRequest(patronRequest)
			.excludedAgencyCode(excludedAgencyCode)
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
