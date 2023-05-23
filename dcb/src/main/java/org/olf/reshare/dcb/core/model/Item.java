package org.olf.reshare.dcb.core.model;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.ZonedDateTime;
import java.util.Comparator;

import static java.util.Comparator.*;
import static org.olf.reshare.dcb.core.model.ItemStatusCode.AVAILABLE;

@Data
@Serdeable
@Builder
@AllArgsConstructor()
public class Item implements Comparable<Item> {
	private final String id;
	private final ItemStatus status;
	@Nullable
	private final ZonedDateTime dueDate;
	private final Location location;
	private final String barcode;
	private final String callNumber;
	private final String hostLmsCode;
	private final Boolean isRequestable;
	private final Integer holdCount;

	public boolean isAvailable() {
		return getStatus().getCode() == AVAILABLE;
	}

	@Override
	public int compareTo(Item other) {
		return nullsLast(CompareByLocationCodeThenCallNumber())
			.compare(this, other);
	}

	private Comparator<Item> CompareByLocationCodeThenCallNumber() {
		return comparing(Item::getLocationCode, nullsLast(naturalOrder()))
			.thenComparing(Item::getCallNumber, nullsLast(naturalOrder()));
	}

	private String getLocationCode() {
		return location == null
			? null
			: location.getCode();
	}
}
