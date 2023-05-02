package org.olf.reshare.dcb.core.api;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import io.micronaut.core.annotation.Nullable;
import org.olf.reshare.dcb.core.model.PatronIdentity;
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
			new Requestor(patronRequest.getPatron().getId().toString(),
				new Agency(patronRequest.getPatronAgencyCode()),
				Identity.fromList(patronRequest.getPatron().getPatronIdentities())),
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
	record Requestor(String id, Agency agency, @Nullable List<Identity> identities) { }

	@Serdeable
	record Identity(String localId, String hostLmsCode, Boolean homeIdentity) {

		private static Identity from(PatronIdentity patronIdentity) {
			return new Identity(patronIdentity.getLocalId(), patronIdentity.getHostLms().code,
				patronIdentity.getHomeIdentity());
		}
		public static List<Identity> fromList(List<PatronIdentity> patronIdentities) {
			return patronIdentities.stream()
				.map(Identity::from)
				.collect(Collectors.toList());
		}
	}


	@Serdeable
	record SupplierRequest(UUID id, Item item, String hostLmsCode) {
		private static SupplierRequest from(
			org.olf.reshare.dcb.core.model.SupplierRequest supplierRequest) {

		return new SupplierRequest(supplierRequest.getId(),
			new Item(supplierRequest.getItemId()),
			supplierRequest.getHostLmsCode());
	}

		private static List<SupplierRequest> fromList(
			List<org.olf.reshare.dcb.core.model.SupplierRequest> supplierRequests) {

			return supplierRequests.stream()
				.map(SupplierRequest::from)
				.collect(Collectors.toList());
		}
	}

	@Serdeable
	record Item(String id) {}

	@Serdeable
	record Status(String code) {}
}
