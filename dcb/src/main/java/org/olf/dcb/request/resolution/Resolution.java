package org.olf.dcb.request.resolution;

import static java.util.Collections.emptyList;
import static lombok.AccessLevel.PRIVATE;
import static org.olf.dcb.utils.PropertyAccessUtils.getValue;
import static org.olf.dcb.utils.PropertyAccessUtils.getValueOrNull;

import java.util.List;
import java.util.UUID;

import org.olf.dcb.core.model.Item;
import org.olf.dcb.item.availability.AvailabilityReport;

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
	@Builder.Default List<Item> excludedItems = emptyList();
	@Builder.Default List<AvailabilityReport.Error> availabilityErrors = emptyList();

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

	public Boolean getIncludeDeletedClusterRecords() {
		return getValueOrNull(parameters, ResolutionParameters::getIncludeDeletedClusterRecords);
	}

	public Resolution trackAllItems(List<Item> allItems) {
		return copy().allItems(allItems).build();
	}

	/**
	 * What availability could not answer. Kept because an empty item list on its own cannot
	 * distinguish a Host LMS that reported no items from one that could not be reached.
	 */
	public Resolution trackAvailabilityErrors(List<AvailabilityReport.Error> availabilityErrors) {
		return copy().availabilityErrors(availabilityErrors).build();
	}

	/** Items a filter removed, each carrying the name of the filter that removed it. */
	public Resolution trackExcludedItems(List<Item> excludedItems) {
		return copy().excludedItems(excludedItems).build();
	}

	private ResolutionBuilder copy() {
		return builder()
			.parameters(parameters)
			.allItems(allItems)
			.filteredItems(filteredItems)
			.sortedItems(sortedItems)
			.excludedItems(excludedItems)
			.availabilityErrors(availabilityErrors)
			.chosenItem(chosenItem);
	}

	public Resolution trackFilteredItems(List<Item> filteredItems) {
		return copy().filteredItems(filteredItems).build();
	}

	public Resolution trackSortedItems(List<Item> sortedItems) {
		return copy().sortedItems(sortedItems).build();
	}

	public Resolution selectItem(Item item) {
		return copy().chosenItem(item).build();
	}

	public List<String> excludedSupplyingAgencyCodes() {
		return getValue(parameters, ResolutionParameters::getExcludedSupplyingAgencyCodes, emptyList());
	}

	public String borrowingAgencyCode() {
		final var borrowingAgencyCode = getValueOrNull(parameters,
			ResolutionParameters::getBorrowingAgencyCode);

		if (borrowingAgencyCode == null) {
			log.warn("Borrowing agency code during resolution is null");
		}

		return borrowingAgencyCode;
	}

	public String pickupAgencyCode() {
		return getValueOrNull(parameters, ResolutionParameters::getPickupAgencyCode);
	}

	public String borrowingHostLmsCode() {
		return getValueOrNull(parameters, ResolutionParameters::getBorrowingHostLmsCode);
	}

	public Boolean isExpeditedCheckout() {
		return getValueOrNull(parameters, ResolutionParameters::getIsExpeditedCheckout);
	}
}
