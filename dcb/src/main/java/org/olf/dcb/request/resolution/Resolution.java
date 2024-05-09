package org.olf.dcb.request.resolution;

import static java.util.Collections.emptyList;
import static java.util.Optional.empty;
import static lombok.AccessLevel.PRIVATE;

import java.util.List;
import java.util.Optional;

import org.olf.dcb.core.model.Item;
import org.olf.dcb.core.model.PatronRequest;

import lombok.Builder;
import lombok.Value;

@Builder(access = PRIVATE)
@Value
public class Resolution {
	PatronRequest patronRequest;
	@Builder.Default Optional<Item> chosenItem = empty();
	@Builder.Default List<Item> allItems = emptyList();
	@Builder.Default List<Item> filteredItems = emptyList();

	public static Resolution forPatronRequest(PatronRequest patronRequest) {
		return builder().patronRequest(patronRequest).build();
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
			.chosenItem(Optional.of(item))
			.build();
	}

	static Resolution noItemsSelectable(PatronRequest patronRequest) {
		return builder()
			.patronRequest(patronRequest)
			.build();
	}
}
