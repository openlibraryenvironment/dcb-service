package org.olf.dcb.request.resolution;

import static java.util.Collections.emptyList;
import static lombok.AccessLevel.PRIVATE;
import static org.olf.dcb.utils.PropertyAccessUtils.getValue;
import static org.olf.dcb.utils.PropertyAccessUtils.getValueOrNull;

import java.util.List;
import java.util.UUID;

import org.olf.dcb.core.model.Item;

import lombok.Builder;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Builder(access = PRIVATE)
@Value
public class Resolution implements ItemFilterParameters {
	ResolutionParameters parameters;

	Item chosenItem;

	@Builder.Default List<Item> allItems = emptyList();
	@Builder.Default List<Item> filteredItems = emptyList();
	@Builder.Default List<Item> sortedItems = emptyList();

	public static Resolution forParameters(ResolutionParameters parameters) {
		return Resolution.builder()
			.parameters(parameters)
			.build();
	}

	public boolean successful() {
		return getChosenItem() != null;
	}

	public UUID getBibClusterId() {
		return getValueOrNull(parameters, ResolutionParameters::getBibClusterId);
	}

	public Resolution trackAllItems(List<Item> allItems) {
		return builder()
			.parameters(parameters)
			.allItems(allItems)
			.filteredItems(filteredItems)
			.sortedItems(sortedItems)
			.chosenItem(chosenItem)
			.build();
	}

	public Resolution trackFilteredItems(List<Item> filteredItems) {
		return Resolution.builder()
			.parameters(parameters)
			.allItems(allItems)
			.filteredItems(filteredItems)
			.sortedItems(sortedItems)
			.chosenItem(chosenItem)
			.build();
	}

	public Resolution trackSortedItems(List<Item> sortedItems) {
		return Resolution.builder()
			.parameters(parameters)
			.allItems(allItems)
			.filteredItems(filteredItems)
			.sortedItems(sortedItems)
			.chosenItem(chosenItem)
			.build();
	}

	public Resolution selectItem(Item item) {
		return Resolution.builder()
			.parameters(parameters)
			.allItems(allItems)
			.filteredItems(filteredItems)
			.sortedItems(sortedItems)
			.chosenItem(item)
			.build();
	}

	public List<String> getExcludedSupplyingAgencyCodes() {
		return getValue(parameters, ResolutionParameters::getExcludedSupplyingAgencyCodes, emptyList());
	}

	public String getBorrowingAgencyCode() {
		final var borrowingAgencyCode = getValueOrNull(parameters,
			ResolutionParameters::getBorrowingAgencyCode);

		if (borrowingAgencyCode == null) {
			log.warn("Patron agency code during resolution is null");
		}

		return borrowingAgencyCode;
	}

	public String getBorrowingHostLmsCode() {
		return getValueOrNull(parameters, ResolutionParameters::getBorrowingHostLmsCode);
	}
}
