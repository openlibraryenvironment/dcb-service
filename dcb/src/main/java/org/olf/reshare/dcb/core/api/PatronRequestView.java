package org.olf.reshare.dcb.core.api;

import java.util.UUID;

import org.olf.reshare.dcb.core.model.PatronRequest;

import io.micronaut.serde.annotation.Serdeable;
@Serdeable
record PatronRequestView(UUID id, Citation citation,
	PickupLocation pickupLocation, Requestor requestor) {

	static PatronRequestView from(PatronRequest patronRequest) {
		return new PatronRequestView(patronRequest.getId(),
			new Citation(patronRequest.getBibClusterId()),
			new PickupLocation(patronRequest.getPickupLocationCode()),
			new Requestor(patronRequest.getPatronId(),
				new Agency(patronRequest.getPatronAgencyCode())));
	}

	@Serdeable
	record PickupLocation(String code) { }

	@Serdeable
	record Citation(UUID bibClusterId) { }

	@Serdeable
	record Agency(String code) { }

	@Serdeable
	record Requestor(String identifier, Agency agency) { }
}
