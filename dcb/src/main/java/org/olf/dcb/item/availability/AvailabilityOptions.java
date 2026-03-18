package org.olf.dcb.item.availability;

import static java.util.Optional.ofNullable;

import java.time.Duration;
import java.util.Optional;

import lombok.Builder;

@Builder
public record AvailabilityOptions(
	Optional<Duration> timeout,
	Optional<String> filters,
	boolean ignoreCache) {

	public static AvailabilityOptions ignoreCache(Optional<Duration> timeout) {
		return AvailabilityOptions.builder()
			.timeout(timeout)
			.filters(applyAllFilters())
			.ignoreCache(true)
			.build();
	}

	public static AvailabilityOptions useCache(Optional<Duration> timeout) {
		return AvailabilityOptions.builder()
			.timeout(timeout)
			.filters(applyAllFilters())
			.ignoreCache(false)
			.build();
	}

	public static AvailabilityOptions useCache(Duration timeout, String filters) {
		final var defaultedFilters = ofNullable(filters).or(AvailabilityOptions::applyAllFilters);

		return AvailabilityOptions.builder()
			.timeout(ofNullable(timeout))
			.filters(defaultedFilters)
			.ignoreCache(false)
			.build();
	}

	private static Optional<String> applyAllFilters() {
		return Optional.of("all");
	}
}
