package org.olf.dcb.request.fulfilment;

import java.util.UUID;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import lombok.Builder;
import lombok.Value;

@Serdeable
@Builder(toBuilder = true)
@Value
public class PlacePatronRequestCommand {
	@NonNull Citation citation;
	@NonNull PickupLocation pickupLocation;
	@NonNull Requestor requestor;
	@Nullable String description;
	@Nullable String requesterNote;
	@Nullable Item item;
	@Nullable Boolean isExpeditedRequest;
	// Set by the entry point from the caller's token, whatever the body says: see StaffClients
	@Nullable Boolean isPlacedByStaff;

	 public String getPickupLocationCode() {
		return getPickupLocation().getCode();
	}

	 public String getRequestorLocalSystemCode() {
		return getRequestor().getLocalSystemCode();
	}

	String getRequestorLocalId() {
		return getRequestor().getLocalId();
	}

	@Serdeable
	@Builder
	@Value
	public static class PickupLocation {
		@NonNull String code;
	}

	@Serdeable
	@Builder
	@Value
	public static class Citation {
		UUID bibClusterId;

		// A specific volume, for example v1 or v3p3; any form VolumeDesignation recognises
		// ("Vol. 3, pt. 3") is matched the same way
		String volumeDesignator;
	}

	@Serdeable
	@Builder
	@Value
	public static class Requestor {
		// The patron ID at the requesting system
		String localId;
		// The code assigned to the hostLMS this user belongs to
		String localSystemCode;
		// THe location code of the patrons home library (May not be present - e.g. FOLIO)
		String homeLibraryCode;
		// The patrons home agency
		String agencyCode;
	}

	@Serdeable
	@Builder
	@Value
	public static class Item {
		// The item ID at the system
		String localId;
		// The code assigned to the hostLMS this item belongs to
		String localSystemCode;
		// The agency code of the item
		String agencyCode;
	}
}
