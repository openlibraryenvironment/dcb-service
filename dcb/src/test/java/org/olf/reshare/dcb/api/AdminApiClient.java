package org.olf.reshare.dcb.api;

import java.util.List;
import java.util.UUID;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.inject.Inject;

class AdminApiClient {
	@Inject
	@Client("/")
	HttpClient client;

	AdminAccessPatronRequest getPatronRequestViaAdminApi(UUID id) {
		return client.toBlocking()
			.retrieve(HttpRequest.GET("/admin/patrons/requests/" + id),
				AdminAccessPatronRequest.class);
	}

	@Serdeable
	static public record AdminAccessPatronRequest(@Nullable UUID id, @Nullable Citation citation,
		@Nullable Requestor requestor, @Nullable PickupLocation pickupLocation,
		@Nullable List<SupplierRequest> supplierRequests, @Nullable Status status) {

		@Serdeable
		record Citation(@Nullable UUID bibClusterId) { }

		@Serdeable
		record Requestor(String id, @Nullable Agency agency, @Nullable List<Identity> identities) { }

		@Serdeable
		record Agency(String code) { }

		@Serdeable
		record Identity(String localId, String hostLmsCode, Boolean homeIdentity) { }

		@Serdeable
		record PickupLocation(String code) { }

		@Serdeable
		record SupplierRequest(UUID id, Item item, String hostLmsCode) {}

		@Serdeable
		record Item(String id) {}

		@Serdeable
		record Status(String code) { }

		// Workaround: records do not support been style properties
		public String getStatusCode() {
			return status().code();
		}
	}
}
