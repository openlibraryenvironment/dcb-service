package org.olf.dcb.request.resolution;

import static java.time.Instant.now;
import static java.time.ZoneId.systemDefault;
import static java.time.temporal.ChronoUnit.DAYS;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.olf.dcb.core.model.ItemStatusCode.AVAILABLE;
import static org.olf.dcb.core.model.ItemStatusCode.CHECKED_OUT;
import static org.olf.dcb.core.model.ItemStatusCode.UNAVAILABLE;
import static org.olf.dcb.core.model.ItemStatusCode.UNKNOWN;

import java.time.Clock;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.olf.dcb.core.model.Item;
import org.olf.dcb.core.model.ItemStatus;
import org.olf.dcb.core.model.ItemStatusCode;

public class AvailabilityDateCalculatorTests {
	private final Instant now = now();

	private final AvailabilityDateCalculator calculator =
		new AvailabilityDateCalculator(Clock.fixed(now, systemDefault()));

	@Test
	void availabilityDateShouldBeNowForAvailableItemsWithNoHolds() {
		// Arrange
		final var item = createItem(AVAILABLE, null, 0);

		// Act
		final var availabilityDate = calculator.calculate(item);

		// Assert
		assertThat(availabilityDate, is(now));
	}

	@ParameterizedTest
	@ValueSource(ints = {1, 2})
	void availabilityDateShouldBeExtendedForAvailableItemsWithHolds(int holdCount) {
		// Arrange
		final var item = createItem(AVAILABLE, null, holdCount);

		// Act
		final var availabilityDate = calculator.calculate(item);

		// Assert
		assertThat(availabilityDate, is(extendDateByDefaultLoanPeriod(now, holdCount)));
	}

	@Test
	void availabilityDateShouldBeNowForAvailableItemsWithNullHoldCount() {
		// Arrange
		final var item = createItem(AVAILABLE, null, null);

		// Act
		final var availabilityDate = calculator.calculate(item);

		// Assert
		assertThat(availabilityDate, is(now));
	}

	@Test
	void availabilityDateShouldBeDueDateForCheckedOutItemsWithNoHolds() {
		// Arrange
		final var dueDate = now.plus(7, DAYS);
		final var item = createItem(CHECKED_OUT, dueDate, 0);

		// Act
		final var availabilityDate = calculator.calculate(item);

		// Assert
		assertThat(availabilityDate, is(dueDate));
	}

	@ParameterizedTest
	@ValueSource(ints = {1, 2})
	void availabilityDateShouldBeExtendedAfterDueDateForCheckedOutItemsWithHolds(int holdCount) {
		// Arrange
		final var dueDate = now.plus(7, DAYS);
		final var item = createItem(CHECKED_OUT, dueDate, holdCount);

		// Act
		final var availabilityDate = calculator.calculate(item);

		// Assert
		assertThat(availabilityDate, is(extendDateByDefaultLoanPeriod(dueDate, holdCount)));
	}

	@Test
	void availabilityDateShouldBeDueDateForCheckedOutItemsWithNullHoldCount() {
		// Arrange
		final var dueDate = now.plus(7, DAYS);
		final var item = createItem(CHECKED_OUT, dueDate, null);

		// Act
		final var availabilityDate = calculator.calculate(item);

		// Assert
		assertThat(availabilityDate, is(dueDate));
	}

	@Test
	void availabilityDateShouldBeArtificiallyExtendedForCheckedOutItemsWithNoDueDate() {
		// Arrange
		final var item = createItem(CHECKED_OUT, null, 0);

		// Act
		final var availabilityDate = calculator.calculate(item);

		// Assert
		assertThat(availabilityDate, is(extendDateByDefaultLoanPeriod(now, 1)));
	}

	@Test
	void availabilityDateShouldBeExtendedFromNowForOverdueItemsWithNoHolds() {
		// Arrange
		final var item = createItem(CHECKED_OUT, now.minus(5, DAYS), 0);

		// Act
		final var availabilityDate = calculator.calculate(item);

		// Assert
		assertThat(availabilityDate, is(extendDateByDefaultLoanPeriod(now, 1)));
	}
	
	@Test
	void availabilityDateShouldBeExtendedFromNowForOverdueItemsWithHolds() {
		// Arrange
		final var item = createItem(CHECKED_OUT, now.minus(5, DAYS), 1);

		// Act
		final var availabilityDate = calculator.calculate(item);

		// Assert
		assertThat(availabilityDate, is(extendDateByDefaultLoanPeriod(now, 2)));
	}

	@Test
	void availabilityDateShouldBeNullForUnavailableItems() {
		// Arrange
		// Unavailable items likely won't have a due date, however
		// one is provided here to demonstrate it is not used
		final var item = createItem(UNAVAILABLE, Instant.now().plus(3, DAYS), 0);

		// Act
		final var availabilityDate = calculator.calculate(item);

		// Assert
		assertThat(availabilityDate, is(nullValue()));
	}

	@Test
	void availabilityDateShouldBeNullForItemInUnknownStatus() {
		// Arrange
		final var item = createItem(UNKNOWN, null, 0);

		// Act
		final var availabilityDate = calculator.calculate(item);

		// Assert
		assertThat(availabilityDate, is(nullValue()));
	}

	@Test
	void availabilityDateShouldBeNullForItemWithNoStatus() {
		// Arrange
		final var item = Item.builder()
			.status(null)
			.dueDate(null)
			.holdCount(null)
			.build();

		// Act
		final var availabilityDate = calculator.calculate(item);

		// Assert
		assertThat(availabilityDate, is(nullValue()));
	}

	private static Item createItem(ItemStatusCode statusCode, Instant dueDate,
		Integer holdCount) {

		return Item.builder()
			.status(new ItemStatus(statusCode))
			.dueDate(dueDate)
			.holdCount(holdCount)
			.build();
	}

	private Instant extendDateByDefaultLoanPeriod(Instant start, int times) {
		final var defaultLoanPeriodInDays = 28L;

		return start.plus(defaultLoanPeriodInDays * times, DAYS);
	}
}
