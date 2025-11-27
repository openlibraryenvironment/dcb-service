package org.olf.dcb.request.resolution;

import static java.time.temporal.ChronoUnit.DAYS;
import static org.olf.dcb.core.model.ItemStatusCode.UNKNOWN;
import static org.olf.dcb.utils.PropertyAccessUtils.getValue;
import static org.olf.dcb.utils.PropertyAccessUtils.getValueOrNull;

import java.time.Clock;
import java.time.Instant;

import org.olf.dcb.core.model.Item;
import org.olf.dcb.core.model.ItemStatus;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AvailabilityDateCalculator {
	private static final long DEFAULT_LOAN_PERIOD_DAYS = 28;

	private final Instant now;

	public AvailabilityDateCalculator() {
		this(Clock.systemUTC());
	}

	public AvailabilityDateCalculator(Clock clock) {
		now = clock.instant();
	}

	public Instant calculate(Item item) {
		final var dueDate = getValueOrNull(item, Item::getDueDate);
		final var holdCount = getValueOrNull(item, Item::getHoldCount);
		final var statusCode = getValue(item, Item::getStatus, ItemStatus::getCode, UNKNOWN);

		log.debug("Deciding available date for item: status: \"{}\", " +
			"due date: \"{}\", hold count: \"{}\"", statusCode, dueDate, holdCount);

		return switch (statusCode) {
			case AVAILABLE -> calculateForAvailableItem(item);
			case CHECKED_OUT -> calculateForCheckedOutItem(item);
			case UNKNOWN, UNAVAILABLE -> null;
		};
	}

	private Instant calculateForAvailableItem(Item item) {
		final var holdCount = getValue(item, Item::getHoldCount, 0);

		return incrementByDefaultLoanPeriod(now, holdCount);
	}

	private Instant calculateForCheckedOutItem(Item item) {
		final var dueDate = getValueOrNull(item, Item::getDueDate);

		if (dueDate != null) {
			final var holdCount = getValue(item, Item::getHoldCount, 0);
			// Check that the due date is not in the past (overdue). If it is, it's likely an overdue in the LMS or our info is out of date - so can't be trusted
			// Before, this led to CHECKED_OUT items with availability dates in the past being prioritised if SELECT_UNAVAILABLE_ITEMS is on
			// To avoid this we must handle pessimistically and increment by the DEFAULT_LOAN_PERIOD, so that CHECKED_OUT items are not prioritised over AVAILABLE ones
			if (dueDate.isBefore(now)) {
				final var calculatedAvailability = incrementByDefaultLoanPeriod(now, holdCount + 1); // +1 to handle zero hold count situations (i.e. Polaris clears holds on check out)
				log.warn("This CHECKED_OUT item has a due date that is in the past (agency code: \"{}\", barcode: \"{}\", dueDate: \"{}\"). " +
							"It has been allocated an availability date of: {}",
						getValue(item, Item::getAgencyCode, "Unknown"),
						getValue(item, Item::getBarcode, "Unknown"),
						dueDate,
						calculatedAvailability);
				return calculatedAvailability;
			}
			// If the due date is not in the past, we handle this situation as before.
			return incrementByDefaultLoanPeriod(dueDate, holdCount);
		}
		else {
			// Pessimistically assume that checked out item without a due date
			// is loaned for default loan period from today
			final var calculatedDueDate = incrementByDefaultLoanPeriod(now, 1);

			final var agencyCode = getValue(item, Item::getAgencyCode, "Unknown");
			final var barcode = getValue(item, Item::getBarcode, "Unknown");

			log.warn("Checked out item without a due date (agency code: \"{}\", barcode: \"{}\") " +
				"has been allocated an availability date: {}", agencyCode, barcode, calculatedDueDate);

			return calculatedDueDate;
		}
	}

	private Instant incrementByDefaultLoanPeriod(Instant start, Integer times) {
		return start.plus(DEFAULT_LOAN_PERIOD_DAYS * times, DAYS);
	}
}
