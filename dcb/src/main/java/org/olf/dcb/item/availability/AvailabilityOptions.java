package org.olf.dcb.item.availability;

import static java.util.Optional.ofNullable;

import java.time.Duration;
import java.util.Optional;

import lombok.Builder;

@Builder
public record AvailabilityOptions(
	Optional<Duration> timeout,
	Optional<String> filters,
	boolean ignoreCache,
	boolean includeDeletedClusterRecords) {

	public static class AvailabilityOptionsBuilder {
		// Define defaults for builder (cannot use annotation with records)
		AvailabilityOptionsBuilder() {
			filters = applyAllFilters();
			ignoreCache = false;
			includeDeletedClusterRecords = true;
		}
	}

	public static AvailabilityOptions ignoreCache(Optional<Duration> timeout,
		boolean includeDeletedClusterRecords) {

		return AvailabilityOptions.builder()
			.timeout(timeout)
			.ignoreCache(true)
			.includeDeletedClusterRecords(includeDeletedClusterRecords)
			.build();
	}

	public static AvailabilityOptions useCache(Optional<Duration> timeout) {
		return AvailabilityOptions.builder()
			.timeout(timeout)
			.build();
	}

	public static AvailabilityOptions useCache(Duration timeout, String filters) {
		final var defaultedFilters = ofNullable(filters).or(AvailabilityOptions::applyAllFilters);

		return AvailabilityOptions.builder()
			.timeout(ofNullable(timeout))
			.filters(defaultedFilters)
			.build();
	}

	private static Optional<String> applyAllFilters() {
		return Optional.of("all");
	}
}
