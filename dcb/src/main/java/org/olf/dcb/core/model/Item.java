package org.olf.dcb.core.model;

import static java.util.Comparator.comparing;
import static java.util.Comparator.naturalOrder;
import static java.util.Comparator.nullsLast;
import static org.olf.dcb.core.model.ItemStatusCode.AVAILABLE;
import static org.olf.dcb.utils.PropertyAccessUtils.getValue;

import java.time.Instant;
import java.util.Comparator;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import lombok.experimental.Accessors;

@Data
@Serdeable
@Builder(toBuilder = true)
@AllArgsConstructor()
@Accessors(chain=true)
public class Item implements Comparable<Item> {
	private String localId;
	private ItemStatus status;
	@Nullable
	private Instant dueDate;
	private Location location;
	private String barcode;
	private String callNumber;
	private Boolean isRequestable;
	private Integer holdCount;
	private String localBibId;
	private String localItemType;
	private String localItemTypeCode;
	private String canonicalItemType;
	private Boolean deleted;
	private Boolean suppressed;
	private DataAgency agency;

	// If this item has attached volume information use these two fields to stash the raw
	// and the processed volume statement. parsed volume statement
	private String rawVolumeStatement;
	private String parsedVolumeStatement;

	public static boolean notSuppressed(Item item) {
		return item.getSuppressed() == null || !item.getSuppressed();
	}

	public boolean isAvailable() {
		return getStatus().getCode() == AVAILABLE;
	}

	public boolean hasNoHolds() {
		return holdCount == null || holdCount == 0;
	}

	public String getLocationCode() {
		return getValue(location, Location::getCode);
	}

	public String getAgencyCode() {
		return getValue(agency, Agency::getCode);
	}

	public HostLms getHostLms() {
		return getValue(agency, Agency::getHostLms);
	}

	public String getHostLmsCode() {
		return getValue(getHostLms(), HostLms::getCode);
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
