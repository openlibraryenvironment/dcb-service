package org.olf.dcb.request.fulfilment;

import java.util.UUID;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotNull;

@Serdeable
public record PlacePatronRequestCommand(
	@NotNull Citation citation,
	@NotNull PickupLocation pickupLocation,
	@NotNull Requestor requestor,
	@Nullable String description) {

	@Serdeable
	public record PickupLocation(String code) { }

	@Serdeable
	public record Citation(UUID bibClusterId) { }

	@Serdeable
	public record Requestor(String localId, String localSystemCode, String homeLibraryCode) { }
}
