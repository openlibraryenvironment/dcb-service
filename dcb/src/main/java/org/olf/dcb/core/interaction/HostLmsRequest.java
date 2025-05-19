package org.olf.dcb.core.interaction;

import io.micronaut.core.annotation.Creator;
import io.micronaut.serde.annotation.Serdeable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import services.k_int.tests.ExcludeFromGeneratedCoverageReport;
import io.micronaut.core.annotation.Nullable;

@Builder
@Data
@NoArgsConstructor(onConstructor_ = @Creator())
@AllArgsConstructor
@Serdeable
@ExcludeFromGeneratedCoverageReport
public class HostLmsRequest {

	public HostLmsRequest(String localId, String status) {
		this.localId = localId;
		this.status = status;
	}

	// Likely that the hold has been deleted from the host system
	public static final String HOLD_MISSING="MISSING";

	// The Hold has been placed, but is not yet available
	public static final String HOLD_PLACED="PLACED";

	// The item for the hold has been chosen / confirmed
	public static final String HOLD_CONFIRMED="CONFIRMED";

	// The Hold is Ready for pickup
	public static final String HOLD_READY="READY";

	// The hold is in transit
	public static final String HOLD_TRANSIT="TRANSIT";

	// The hold is cancelled
	public static final String HOLD_CANCELLED="CANCELLED";

	@Nullable
	String localId;

	@Nullable
	String localPatronId;

	// N.B. That if we are unable to map a status, this string MAY contain a value not from the set above!
	@Nullable
	String status;

	// The local status that hasn't been altered
	@Nullable
	String rawStatus;

	/** Once known, the local id of the item actually requested */
	@Nullable
	String requestedItemId;

	/** Once known, the local barcode of the item actually requested */
	@Nullable
	String requestedItemBarcode;
}
