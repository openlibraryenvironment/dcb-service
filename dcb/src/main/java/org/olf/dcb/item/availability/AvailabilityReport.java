package org.olf.dcb.item.availability;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.time.Instant;

import org.olf.dcb.core.model.Item;

import lombok.Builder;
import lombok.Data;
import lombok.Singular;
import lombok.experimental.Accessors;
import reactor.util.function.Tuple2;

@Data
@Builder(toBuilder = true)
@Accessors(chain = true)
public class AvailabilityReport {

	private final List<Item> items;
	private final List<Error> errors;

	// This tells us when the report was created so that cached reports can indicate their age
	private final Instant reportCreateTime = Instant.now();
	
	@Singular
	private final List<Tuple2<String, Long>> timings;

	private static AvailabilityReport of(List<Item> items, List<Tuple2<String, Long>> timings, List<Error> errors) {
		
		return builder()
			.items(items)
			.timings(timings)
			.errors(errors)
			.build();
	}

	public static AvailabilityReport ofItems(List<Item> items) {
		return of(items, Collections.emptyList(), Collections.emptyList());
	}

	public static AvailabilityReport ofItems(List<Item> items, List<Tuple2<String, Long>> timings, List<Error> errors) {
		return of(items, timings, errors);
	}

	public static AvailabilityReport ofErrors(Error error, Tuple2<String, Long> timing) {
		return of(Collections.emptyList(), List.of(timing), List.of(error));
	}
	
	public static AvailabilityReport ofErrors(Error error) {
		return of(Collections.emptyList(), Collections.emptyList(), List.of(error));
	}

	public static AvailabilityReport emptyReport() {
		return of(Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
	}

	static AvailabilityReport combineReports(
		AvailabilityReport firstReport, AvailabilityReport secondReport) {

		final var newItemsList = new ArrayList<Item>(firstReport.getItems());
		newItemsList.addAll(secondReport.getItems());

		final var newErrorsList = new ArrayList<Error>(firstReport.getErrors());
		newErrorsList.addAll(secondReport.getErrors());
		
		final var newTimings = new ArrayList<Tuple2<String, Long>>(firstReport.getTimings());
		newTimings.addAll(secondReport.getTimings());

		return of(newItemsList, newTimings, newErrorsList);
	}

	AvailabilityReport forEachItem(Consumer<Item> consumer) {
		getItems().forEach(consumer);

		return this;
	}

	AvailabilityReport sortItems() {
		return of(sortItems(items), timings, errors);
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
