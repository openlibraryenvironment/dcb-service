package org.olf.dcb.request.resolution;

import static java.time.Instant.now;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.olf.dcb.core.model.ItemStatusCode.AVAILABLE;

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
		final var item = createItem(AVAILABLE);

		// Act
		final var availabilityDate = calculator.calculate(item);

		// Assert
		assertThat(availabilityDate, is(now));
	}

	private static Item createItem(ItemStatusCode statusCode) {
		return Item.builder().status(new ItemStatus(statusCode)).build();
	}
}
