package org.olf.dcb.request.resolution;

import static java.util.Collections.emptyList;
import static lombok.AccessLevel.PRIVATE;
import static org.olf.dcb.utils.PropertyAccessUtils.getValue;

import java.util.List;
import java.util.UUID;

import org.olf.dcb.core.model.Item;
import org.olf.dcb.core.model.PatronRequest;

import lombok.Builder;
import lombok.Value;

@Builder(access = PRIVATE)
@Value
public class Resolution {
	PatronRequest patronRequest;
	Item chosenItem;
	@Builder.Default List<Item> allItems = emptyList();
	@Builder.Default List<Item> filteredItems = emptyList();

	public static Resolution forPatronRequest(PatronRequest patronRequest) {
		return builder().patronRequest(patronRequest).build();
	}

	public UUID getBibClusterId() {
		return getValue(patronRequest, PatronRequest::getBibClusterId);
	}

	public Resolution trackAllItems(List<Item> allItems) {
		return builder()
			.patronRequest(patronRequest)
			.allItems(allItems)
			.build();
	}

	public Resolution trackFilteredItems(List<Item> filteredItems) {
		return Resolution.builder()
			.patronRequest(patronRequest)
			.allItems(allItems)
			.filteredItems(filteredItems)
			.build();
	}

	public Resolution selectItem(Item item) {
		return Resolution.builder()
			.patronRequest(patronRequest)
			.allItems(allItems)
			.filteredItems(filteredItems)
			.chosenItem(item)
			.build();
	}

	static Resolution noItemsSelectable(PatronRequest patronRequest) {
		return builder()
			.patronRequest(patronRequest)
			.build();
	}
}
