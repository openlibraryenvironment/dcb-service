package org.olf.dcb.request.fulfilment;

import java.util.UUID;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import lombok.Builder;
import lombok.Value;

@Serdeable
@Builder
@Value
public class PlacePatronRequestCommand {
	@NonNull Citation citation;
	@NonNull PickupLocation pickupLocation;
	@NonNull Requestor requestor;
	@Nullable String description;
	@Nullable String requesterNote;
	@Nullable Item item;
	@Nullable Boolean isExpeditedRequest;

	String getPickupLocationCode() {
		return getPickupLocation().getCode();
	}

	String getPickupLocationContext() {
		return getPickupLocation().getContext();
	}

	String getRequestorLocalSystemCode() {
		return getRequestor().getLocalSystemCode();
	}

	String getRequestorAgencyCode() {
		return getRequestor().getAgencyCode();
	}

	String getRequestorLocalId() {
		return getRequestor().getLocalId();
	}

	@Serdeable
	@Builder
	@Value
	public static class PickupLocation {
		String context;
		@NonNull String code;
	}

	@Serdeable
	@Builder
	@Value
	public static class Citation {
		UUID bibClusterId;

		// If the user needs a specific volume, this designator holds the DCB normalised
		// value, for example v1 for volume 1, v3p3 for Volume 3 Part 3. It is important to pass
		// the normalised value and not the citation value
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
