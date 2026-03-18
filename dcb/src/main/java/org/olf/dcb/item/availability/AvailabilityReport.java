package org.olf.dcb.item.availability;

import static java.util.Collections.emptyList;

import java.util.ArrayList;
import java.util.List;
import java.time.Instant;

import org.olf.dcb.core.model.Item;

import lombok.Builder;
import lombok.Data;
import lombok.Singular;
import lombok.experimental.Accessors;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

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
		return of(items, emptyList(), emptyList());
	}

	public static AvailabilityReport ofItems(List<Item> items, List<Tuple2<String, Long>> timings, List<Error> errors) {
		return of(items, timings, errors);
	}

	public static AvailabilityReport ofErrors(Error error, Tuple2<String, Long> timing) {
		return of(emptyList(), List.of(timing), List.of(error));
	}
	
	public static AvailabilityReport ofErrors(Error error) {
		return of(emptyList(), emptyList(), List.of(error));
	}

	public static AvailabilityReport emptyReport() {
		return of(emptyList(), emptyList(), emptyList());
	}

	static AvailabilityReport combineReports(
		AvailabilityReport firstReport, AvailabilityReport secondReport) {

		final var newItemsList = new ArrayList<>(firstReport.getItems());
		newItemsList.addAll(secondReport.getItems());

		final var newErrorsList = new ArrayList<>(firstReport.getErrors());
		newErrorsList.addAll(secondReport.getErrors());
		
		final var newTimings = new ArrayList<>(firstReport.getTimings());
		newTimings.addAll(secondReport.getTimings());

		return of(newItemsList, newTimings, newErrorsList);
	}

	AvailabilityReport sortItems() {
		return of(sortItems(items), timings, errors);
	}

	private List<Item> sortItems(List<Item> items) {
		return items.stream().sorted().toList();
	}

	AvailabilityReport withTotalElapsedTime(long elapsed) {
		return toBuilder()
			.timing(Tuples.of("total", elapsed))
			.build();
	}

	@Data
	@Builder
	public static class Error {
		private final String message;
	}
}
