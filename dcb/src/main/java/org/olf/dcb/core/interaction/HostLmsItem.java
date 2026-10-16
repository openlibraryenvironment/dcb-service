package org.olf.dcb.core.interaction;

import java.time.Instant;

import io.micronaut.core.annotation.Creator;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import services.k_int.tests.ExcludeFromGeneratedCoverageReport;

@Builder
@Data
@NoArgsConstructor(onConstructor_ = @Creator())
@AllArgsConstructor
@Serdeable
@ExcludeFromGeneratedCoverageReport
public class HostLmsItem {
	// Likely that the hold has been deleted from the host system
	public static final String ITEM_MISSING = "MISSING";

	// The Hold has been placed, but is not yet available
	public static final String ITEM_AVAILABLE = "AVAILABLE";

	// The hold is in transit
	public static final String ITEM_TRANSIT = "TRANSIT";

	// Item is off-site
	public static final String ITEM_OFFSITE = "OFFSITE";

	// Item on hold shelf
	public static final String ITEM_ON_HOLDSHELF = "HOLDSHELF";

	public static final String ITEM_RECEIVED = "RECEIVED";
	public static final String LIBRARY_USE_ONLY = "LIBRARY_USE_ONLY";
	public static final String ITEM_RETURNED = "RETURNED";
	public static final String ITEM_REQUESTED = "REQUESTED";

	// Item is off-site
	public static final String ITEM_LOANED = "LOANED";

	String localId;
	String localRequestId;
	String status;
	// The local status that hasn't been altered
	@Nullable
	String rawStatus;
	String barcode;
	@Nullable
	Integer renewalCount;
	@Nullable
	Boolean renewable;

	// When the item is due back, where the Host LMS says so on the item read this was built
	// from. NULL MEANS UNKNOWN, never "not on loan": several adapters answer item status from a
	// payload that carries no date, and a patron told "no due date" for an item they are
	// holding would plan around it.
	@Nullable
	Instant dueDate;

	// Aggregated hold count - for DCB purposes we want to convey itemHolds+titleHolds into this number
	// We don't care for our purposes what the hold has been placed on, only that someone has dibs on this item.
	@Nullable
	Integer holdCount;

	@Nullable
	String holdingId;

	@Nullable
	String bibId;
}
