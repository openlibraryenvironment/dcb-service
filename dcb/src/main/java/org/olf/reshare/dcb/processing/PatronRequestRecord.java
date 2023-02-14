package org.olf.reshare.dcb.processing;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.UUID;

@Serdeable
public record PatronRequestRecord(
	@Nullable UUID id,
	@NotNull
	@NotBlank
	Citation citation,
	@NotNull
	@NotBlank
	PickupLocation pickupLocation,
	@NotNull
	@NotBlank
	Requestor requestor
){
	@Serdeable
	public record PickupLocation(
		String code
	) {
	}
	@Serdeable
	public record Citation(
		UUID bibClusterId
	) {
	}
	@Serdeable
	public record Agency(
		String code
	) {
	}
	@Serdeable
	public record Requestor(
		String identifier,
		Agency agency
	) {


	}
}
