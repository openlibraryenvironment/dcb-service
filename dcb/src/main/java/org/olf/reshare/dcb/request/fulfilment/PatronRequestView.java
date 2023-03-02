package org.olf.reshare.dcb.request.fulfilment;

import java.util.List;
import java.util.UUID;

import io.micronaut.serde.annotation.Serdeable;
@Serdeable
public record PatronRequestView(UUID id, Citation citation,
	PickupLocation pickupLocation,
	Requestor requestor,
	List<SupplierRequest> supplierRequests) {

	@Serdeable
	public record PickupLocation(String code) { }
	@Serdeable
	public record Citation(UUID bibClusterId) { }
	@Serdeable
	public record Agency(String code) { }
	@Serdeable
	public record Requestor(String identifier, Agency agency) { }

	@Serdeable
	public record SupplierRequest(UUID id, Item item, Agency agency) {}

	@Serdeable
	public record Item(UUID id) {}

}
