package org.olf.dcb.request.resolution;

import static org.olf.dcb.utils.PropertyAccessUtils.getValueOrNull;

import java.time.Clock;
import java.time.Instant;

import org.olf.dcb.core.model.Item;
import org.olf.dcb.core.model.ItemStatus;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AvailabilityDateCalculator {
	private final Instant now;

	public AvailabilityDateCalculator() {
		this(Clock.systemUTC());
	}

	public AvailabilityDateCalculator(Clock clock) {
		now = clock.instant();
	}

	public Instant calculate(Item item) {
		return calculate(item.getStatus(), item.getDueDate());
	}

	private Instant calculate(ItemStatus itemStatus, Instant dueDate) {
		log.debug("Deciding available date for item status: {} and due date: {}", itemStatus, dueDate);

		final var statusCode = getValueOrNull(itemStatus, ItemStatus::getCode);

		return switch (statusCode) {
			case AVAILABLE, UNAVAILABLE, UNKNOWN -> now;
			case CHECKED_OUT -> dueDate != null ? dueDate : now;
		};
	}
}
