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
	static
	record AdminAccessPatronRequest(@Nullable UUID id, @Nullable Citation citation,
		@Nullable Requestor requestor, @Nullable PickupLocation pickupLocation,
		@Nullable List<SupplierRequest> supplierRequests) {

		@Serdeable
		record Citation(@Nullable UUID bibClusterId) { }

		@Serdeable
		record Requestor(String identifier, @Nullable Agency agency) { }

		@Serdeable
		record Agency(String code) { }

		@Serdeable
		record PickupLocation(String code) { }

		@Serdeable
		record SupplierRequest(UUID id, Item item, Agency agency) {}

		@Serdeable
		record Item(UUID id) {}
	}
}
