package org.olf.dcb.item.availability;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.olf.dcb.core.model.Item;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AvailabilityReport {
	private final List<Item> items;
	private final List<Error> errors;

	public static AvailabilityReport of(List<Item> items, List<Error> errors) {
		return builder()
			.items(items)
			.errors(errors)
			.build();
	}

	public static AvailabilityReport ofItems(List<Item> items) {
		return of(items, List.of());
	}

	public static AvailabilityReport ofItems(Item... items) {
		return ofItems(List.of(items));
	}

	public static AvailabilityReport ofErrors(Error... errors) {
		return of(List.of(), List.of(errors));
	}

	public static AvailabilityReport emptyReport() {
		return of(List.of(), List.of());
	}

	static AvailabilityReport combineReports(
		AvailabilityReport firstReport, AvailabilityReport secondReport) {

		final var newItemsList = new ArrayList<Item>();

		newItemsList.addAll(firstReport.getItems());
		newItemsList.addAll(secondReport.getItems());

		final var newErrorsList = new ArrayList<Error>();

		newErrorsList.addAll(firstReport.getErrors());
		newErrorsList.addAll(secondReport.getErrors());

		return of(newItemsList, newErrorsList);
	}

	AvailabilityReport forEachItem(Consumer<Item> consumer) {
		getItems().forEach(consumer);

		return this;
	}

	AvailabilityReport sortItems() {
		return of(sortItems(items), errors);
	}

	private List<Item> sortItems(List<Item> items) {
		return items.stream().sorted().toList();
	}


	@Data
	@Builder
	public static class Error {
		private final String message;
	}
}
