package org.olf.reshare.dcb.request.fulfilment;

import java.util.UUID;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record PlacePatronRequestCommand(
	@NotNull @NotBlank Citation citation,
	@NotNull @NotBlank PickupLocation pickupLocation,
	@NotNull @NotBlank Requestor requestor) {

	@Serdeable
	public record PickupLocation(String code) { }
	@Serdeable
	public record Citation(UUID bibClusterId) { }
	@Serdeable
	public record Agency(String code) { }
	@Serdeable
	public record Requestor(Agency agency, String localId, String localSystemCode) { }
}
