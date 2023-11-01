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
