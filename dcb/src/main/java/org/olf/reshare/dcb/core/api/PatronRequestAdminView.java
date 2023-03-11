package org.olf.reshare.dcb.core.api;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.olf.reshare.dcb.core.model.PatronRequest;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
record PatronRequestAdminView(UUID id, Citation citation,
	PickupLocation pickupLocation, Requestor requestor,
	List<SupplierRequest> supplierRequests, Status status) {

	static PatronRequestAdminView from(PatronRequest patronRequest,
		List<org.olf.reshare.dcb.core.model.SupplierRequest> supplierRequests) {

		return new PatronRequestAdminView(patronRequest.getId(),
			new Citation(patronRequest.getBibClusterId()),
			new PickupLocation(patronRequest.getPickupLocationCode()),
			new Requestor(patronRequest.getPatronId(),
				new Agency(patronRequest.getPatronAgencyCode())),
			SupplierRequest.fromList(supplierRequests),
			new Status(patronRequest.getStatusCode()));
	}

	@Serdeable
	record PickupLocation(String code) { }

	@Serdeable
	record Citation(UUID bibClusterId) { }

	@Serdeable
	record Agency(String code) { }

	@Serdeable
	record Requestor(String identifier, Agency agency) { }

	@Serdeable
	record SupplierRequest(UUID id, Item item, Agency agency) {
		private static SupplierRequest from(
			org.olf.reshare.dcb.core.model.SupplierRequest supplierRequest) {

		return new SupplierRequest(supplierRequest.getId(),
			new Item(supplierRequest.getHoldingsItemId()),
			new Agency(supplierRequest.getHoldingsAgencyCode()));
	}

		private static List<SupplierRequest> fromList(
			List<org.olf.reshare.dcb.core.model.SupplierRequest> supplierRequests) {

			return supplierRequests.stream()
				.map(SupplierRequest::from)
				.collect(Collectors.toList());
		}
	}

	@Serdeable
	record Item(UUID id) {}

	@Serdeable
	record Status(String code) {}
}
