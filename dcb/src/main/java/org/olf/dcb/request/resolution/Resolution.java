package org.olf.dcb.request.resolution;

import static java.util.Collections.emptyList;
import static lombok.AccessLevel.PRIVATE;
import static org.olf.dcb.utils.PropertyAccessUtils.getValue;
import static org.olf.dcb.utils.PropertyAccessUtils.getValueOrNull;

import java.util.List;
import java.util.UUID;

import org.olf.dcb.core.model.Item;
import org.olf.dcb.core.model.Patron;
import org.olf.dcb.core.model.PatronRequest;

import lombok.Builder;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Builder(access = PRIVATE)
@Value
public class Resolution implements ItemFilterParameters {
	PatronRequest patronRequest;
	ResolutionParameters parameters;

	Item chosenItem;

	@Builder.Default List<Item> allItems = emptyList();
	@Builder.Default List<Item> filteredItems = emptyList();
	@Builder.Default List<Item> sortedItems = emptyList();

	public static Resolution forParameters(PatronRequest patronRequest,
		List<String> excludedAgencyCodes) {
		return Resolution.builder()
			.patronRequest(patronRequest)
			.parameters(ResolutionParameters.builder()
				.patron(getValueOrNull(patronRequest, PatronRequest::getPatron))
				.bibClusterId(getValueOrNull(patronRequest, PatronRequest::getBibClusterId))
				.pickupLocationCode(getValueOrNull(patronRequest, PatronRequest::getPickupLocationCode))
				.excludedAgencyCodes(excludedAgencyCodes)
				.patronHostLmsCode(getValueOrNull(patronRequest, PatronRequest::getPatronHostlmsCode))
				.manualItemSelection(ManualItemSelection.builder()
					.localItemId(getValueOrNull(patronRequest, PatronRequest::getLocalItemId))
					.hostLmsCode(getValueOrNull(patronRequest, PatronRequest::getLocalItemHostlmsCode))
					.agencyCode(getValueOrNull(patronRequest, PatronRequest::getLocalItemAgencyCode))
					.build())
				.build())
			.build();
	}

	public boolean successful() {
		return getChosenItem() != null;
	}

	public Resolution withPatronRequest(PatronRequest newPatronRequest) {
		return builder()
			.patronRequest(newPatronRequest)
			.parameters(parameters)
			.chosenItem(chosenItem)
			.allItems(allItems)
			.filteredItems(filteredItems)
			.sortedItems(sortedItems)
			.chosenItem(chosenItem)
			.build();
	}

	public UUID getBibClusterId() {
		return getValueOrNull(parameters, ResolutionParameters::getBibClusterId);
	}

	public Resolution trackAllItems(List<Item> allItems) {
		return builder()
			.patronRequest(patronRequest)
			.parameters(parameters)
			.allItems(allItems)
			.filteredItems(filteredItems)
			.sortedItems(sortedItems)
			.chosenItem(chosenItem)
			.build();
	}

	public Resolution trackFilteredItems(List<Item> filteredItems) {
		return Resolution.builder()
			.patronRequest(patronRequest)
			.parameters(parameters)
			.allItems(allItems)
			.filteredItems(filteredItems)
			.sortedItems(sortedItems)
			.chosenItem(chosenItem)
			.build();
	}

	public Resolution trackSortedItems(List<Item> sortedItems) {
		return Resolution.builder()
			.patronRequest(patronRequest)
			.parameters(parameters)
			.allItems(allItems)
			.filteredItems(filteredItems)
			.sortedItems(sortedItems)
			.chosenItem(chosenItem)
			.build();
	}

	public Resolution selectItem(Item item) {
		return Resolution.builder()
			.patronRequest(patronRequest)
			.parameters(parameters)
			.allItems(allItems)
			.filteredItems(filteredItems)
			.sortedItems(sortedItems)
			.chosenItem(item)
			.build();
	}

	public List<String> getExcludedAgencyCodes() {
		return getValue(parameters, ResolutionParameters::getExcludedAgencyCodes, emptyList());
	}

	public String getBorrowingAgencyCode() {
		final var patron = getValueOrNull(parameters, ResolutionParameters::getPatron);
		final var borrowingAgencyCode = getValueOrNull(patron, Patron::determineBorrowingAgencyCode);

		if (borrowingAgencyCode == null) {
			log.warn("Borrowing agency code during resolution is null");
		}

		return borrowingAgencyCode;
	}

	public String getBorrowingHostLmsCode() {
		return getValueOrNull(parameters, ResolutionParameters::getPatronHostLmsCode);
	}

}
