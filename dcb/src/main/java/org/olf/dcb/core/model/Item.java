package org.olf.dcb.core.model;

import static java.util.Comparator.comparing;
import static java.util.Comparator.naturalOrder;
import static java.util.Comparator.nullsLast;
import static org.olf.dcb.core.model.ItemStatusCode.AVAILABLE;

import java.time.ZonedDateTime;
import java.util.Comparator;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import lombok.experimental.Accessors;


@Data
@Serdeable
@Builder
@AllArgsConstructor()
@Accessors(chain=true)
public class Item implements Comparable<Item> {
	private String id;
	private ItemStatus status;
	@Nullable
	private ZonedDateTime dueDate;
	private Location location;
	private String barcode;
	private String callNumber;
	private String hostLmsCode;
	private Boolean isRequestable;
	private Integer holdCount;
	private String bibId;
	private String localItemType;
	private String localItemTypeCode;
	private String canonicalItemType;
	private Boolean deleted;
	private Boolean suppressed;
	private String agencyCode;
	private String agencyDescription;

	public boolean isAvailable() {
		return getStatus().getCode() == AVAILABLE;
	}

	public boolean hasNoHolds() {
		return holdCount == null || holdCount == 0;
	}

	public String getLocationCode() {
		return location == null
			? null
			: location.getCode();
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
}
