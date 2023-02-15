package org.olf.reshare.dcb.request.fulfilment;

import java.util.UUID;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record PatronRequestView(UUID id, Citation citation,
		PickupLocation pickupLocation, Requestor requestor) {

	@Serdeable
	public record PickupLocation(String code) { }
	@Serdeable
	public record Citation(UUID bibClusterId) { }
	@Serdeable
	public record Agency(String code) { }
	@Serdeable
	public record Requestor(String identifier, Agency agency) { }
}
