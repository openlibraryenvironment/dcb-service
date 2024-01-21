package org.olf.dcb.request.fulfilment;

import java.util.UUID;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Value;

@Serdeable
@Builder
@Value
public class PlacePatronRequestCommand {
	@NotNull Citation citation;
	@NotNull PickupLocation pickupLocation;
	@NotNull Requestor requestor;
	@Nullable String description;

	String getPickupLocationCode() {
		return getPickupLocation().getCode();
	}

	String getPickupLocationContext() {
		return getPickupLocation().getContext();
	}

	String getRequestorLocalSystemCode() {
		return getRequestor().getLocalSystemCode();
	}

	String getRequestorLocalId() {
		return getRequestor().getLocalId();
	}

	@Serdeable
	@Builder
	@Value
	public static class PickupLocation {
		String context;
		String code;
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
		String localId;
		String localSystemCode;
		String homeLibraryCode;
	}
}
