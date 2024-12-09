package org.olf.dcb.request.resolution;

import static java.time.Instant.now;
import static java.time.temporal.ChronoUnit.DAYS;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.olf.dcb.core.model.ItemStatusCode.AVAILABLE;
import static org.olf.dcb.core.model.ItemStatusCode.CHECKED_OUT;
import static org.olf.dcb.core.model.ItemStatusCode.UNAVAILABLE;
import static org.olf.dcb.core.model.ItemStatusCode.UNKNOWN;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;
import org.olf.dcb.core.model.Item;
import org.olf.dcb.core.model.ItemStatus;
import org.olf.dcb.core.model.ItemStatusCode;

public class AvailabilityDateCalculatorTests {
	private final Instant now = now();
	private final AvailabilityDateCalculator calculator =
		new AvailabilityDateCalculator(Clock.fixed(now, ZoneId.systemDefault()));

	@Test
	void availabilityDateShouldBeNowForAvailableItems() {
		// Arrange
		final var item = createItem(AVAILABLE, null);

		// Act
		final var availabilityDate = calculator.calculate(item);

		// Assert
		assertThat(availabilityDate, is(now));
	}

	@Test
	void availabilityDateShouldBeDueDateForCheckedOutItems() {
		// Arrange
		final var dueDate = now.plus(7, DAYS);
		final var item = createItem(CHECKED_OUT, dueDate);

		// Act
		final var availabilityDate = calculator.calculate(item);

		// Assert
		assertThat(availabilityDate, is(dueDate));
	}

	@Test
	void availabilityDateShouldBeNowForCheckedOutItemsWithNoDueDate() {
		// Arrange
		final var item = createItem(CHECKED_OUT, null);

		// Act
		final var availabilityDate = calculator.calculate(item);

		// Assert
		assertThat(availabilityDate, is(now));
	}

	@Test
	void availabilityDateShouldBeNowForUnavailableItem() {
		// Arrange
		final var item = createItem(UNAVAILABLE, null);

		// Act
		final var availabilityDate = calculator.calculate(item);

		// Assert
		assertThat(availabilityDate, is(now));
	}

	@Test
	void availabilityDateShouldBeNowForItemInUnknownStatus() {
		// Arrange
		final var item = createItem(UNKNOWN, null);

		// Act
		final var availabilityDate = calculator.calculate(item);

		// Assert
		assertThat(availabilityDate, is(now));
	}

	private static Item createItem(ItemStatusCode statusCode, Instant dueDate) {
		return Item.builder()
			.status(new ItemStatus(statusCode))
			.dueDate(dueDate)
			.build();
	}
}
