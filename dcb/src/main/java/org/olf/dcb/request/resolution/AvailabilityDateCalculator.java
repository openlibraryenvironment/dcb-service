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
		final var statusCode = getValue(item, Item::getStatus, ItemStatus::getCode, UNKNOWN);

		log.debug("Deciding available date for item status: {} and due date: {}", statusCode, dueDate);

		return switch (statusCode) {
			case AVAILABLE -> now;
			case CHECKED_OUT -> {
				if (dueDate != null) {
					yield dueDate;
				}
				else {
					// Pessimistically assume that checked out item without a due date
					// is loaned for default loan period from today
					final var calculatedDueDate = incrementByDefaultLoanPeriod(now, 1);

					final var agencyCode = getValue(item, Item::getAgencyCode, "Unknown");
					final var barcode = getValue(item, Item::getBarcode, "Unknown");

					log.warn("Checked out item without a due date (agency code: \"{}\", barcode: \"{}\") " +
						"has been allocated an availability date: {}", agencyCode, barcode, calculatedDueDate);

					yield calculatedDueDate;
				}
			}
			case UNKNOWN, UNAVAILABLE -> null;
		};
	}

	private Instant incrementByDefaultLoanPeriod(Instant start, Integer times) {
		return start.plus(DEFAULT_LOAN_PERIOD_DAYS * times, DAYS);
	}
}
