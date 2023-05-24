package org.olf.reshare.dcb.api;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.inject.Inject;

import java.util.List;
import java.util.UUID;

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
	public record AdminAccessPatronRequest(@Nullable UUID id,
		@Nullable Citation citation, @Nullable Requestor requestor,
		@Nullable PickupLocation pickupLocation,
		@Nullable List<SupplierRequest> supplierRequests, @Nullable Status status) {

		@Serdeable
		record Citation(@Nullable UUID bibClusterId) { }

		@Serdeable
		record Requestor(@Nullable String id, @Nullable String homeLibraryCode,
			@Nullable List<Identity> identities) {
		}

		@Serdeable
		record Identity(@Nullable String localId, @Nullable String hostLmsCode,
			@Nullable Boolean homeIdentity) { }

		@Serdeable
		record PickupLocation(@Nullable String code) { }

		@Serdeable
		record SupplierRequest(@Nullable UUID id, @Nullable Item item,
			@Nullable String hostLmsCode, @Nullable String status,
		 	@Nullable String localHoldId, @Nullable String localHoldStatus) {}

		@Serdeable
		record Item(@Nullable String id, @Nullable String localItemBarcode) {}

		@Serdeable
		record Status(@Nullable String code) { }

		// Workaround: records do not support been style properties
		public String getStatusCode() {
			return status().code();
		}
	}
}
