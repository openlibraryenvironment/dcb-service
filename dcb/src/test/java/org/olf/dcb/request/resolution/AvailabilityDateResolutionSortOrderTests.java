package org.olf.dcb.request.resolution;

import static java.time.Instant.now;
import static java.time.temporal.ChronoUnit.DAYS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.olf.dcb.core.model.Item;

public class AvailabilityDateResolutionSortOrderTests {
	private final AvailabilityDateResolutionSortOrder sortOrder = new AvailabilityDateResolutionSortOrder();

	@Test
	void shouldSortItemsInAscendingAvailabilityDateOrder() {
		// Arrange
		final var availableNow = createItem(now());
		final var availableInOneWeek = createItem(now().plus(7, DAYS));
		final var availableInTwoWeeks = createItem(now().plus(14, DAYS));

		final var items = List.of(availableInTwoWeeks, availableNow, availableInOneWeek);

		// Act
		final var sortedItems = sort(items);

		// Assert
		assertThat(sortedItems, contains(availableNow, availableInOneWeek, availableInTwoWeeks));
	}

	@Test
	void shouldSortNullAvailabilityDateItemsLast() {
		// Arrange
		final var availableNow = createItem(now());
		final var nullAvailabilityDate = createItem(null);

		final var items = List.of(nullAvailabilityDate, availableNow);

		// Act
		final var sortedItems = sort(items);

		// Assert
		assertThat(sortedItems, contains(availableNow, nullAvailabilityDate));
	}

	private List<Item> sort(List<Item> items) {
		return singleValueFrom(sortOrder.sortItems(
			ResolutionSortOrder.Parameters.builder()
				.items(items)
				.pickupLocationCode("some-pickup-location")
				.build()));
	}

	private static Item createItem(Instant availabilityDate) {
		return Item.builder()
			.availableDate(availabilityDate)
			.build();
	}
}
