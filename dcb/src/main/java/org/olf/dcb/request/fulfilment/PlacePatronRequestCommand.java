package org.olf.dcb.request.fulfilment;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import io.micronaut.core.annotation.Nullable;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record PlacePatronRequestCommand(
	@NotNull @NotBlank Citation citation,
	@NotNull @NotBlank PickupLocation pickupLocation,
	@NotNull @NotBlank Requestor requestor,
        @Nullable String description) {

	@Serdeable
	public record PickupLocation(String code) { }

	@Serdeable
	public record Citation(UUID bibClusterId) { }

	@Serdeable
	public record Requestor(String localId, String localSystemCode, String homeLibraryCode) { }
}
