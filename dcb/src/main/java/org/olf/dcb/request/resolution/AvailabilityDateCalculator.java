package org.olf.dcb.request.resolution;

import static org.olf.dcb.core.model.ItemStatusCode.CHECKED_OUT;

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
		if (itemStatus.getCode() == CHECKED_OUT && dueDate != null) {
			log.debug("Item is checked out and has a due date, using due date as available date");
			return dueDate;
		} else {
			log.debug("Item is not checked out or does not have a due date, using current time as available date");
			return now;
		}
	}
}
