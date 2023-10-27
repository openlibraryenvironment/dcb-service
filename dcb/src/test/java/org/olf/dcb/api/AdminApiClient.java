package org.olf.dcb.api;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.olf.dcb.core.model.PatronRequest;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.inject.Inject;
import lombok.Value;

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
	@Value
	public static class AdminAccessPatronRequest {
		@Nullable UUID id;
		@Nullable Citation citation;
		@Nullable Requestor requestor;
		@Nullable PickupLocation pickupLocation;
		@Nullable List<SupplierRequest> supplierRequests;
		@Nullable Status status;
		LocalRequest localRequest;
		@Nullable List<Audit> audits;

		@Serdeable
		@Value
		public static class Citation {
			@Nullable UUID bibClusterId;
		}

		@Serdeable
		@Value
		public static class Requestor {
			@Nullable String id;
			@Nullable String homeLibraryCode;
			@Nullable List<Identity> identities;
		}

		@Serdeable
		@Value
		public static class Identity {
			@Nullable String localId;
			@Nullable String hostLmsCode;
			@Nullable Boolean homeIdentity;
		}

		@Serdeable
		@Value
		public static class PickupLocation {
			@Nullable String code;
		}

		@Serdeable
		@Value
		public static class SupplierRequest {
			@Nullable UUID id;
			@Nullable Item item;
			@Nullable String hostLmsCode;
			@Nullable String status;
			@Nullable String localHoldId;
			@Nullable String localHoldStatus;
		}

		@Serdeable
		@Value
		public static class Item {
			@Nullable String id;
			@Nullable String localItemBarcode;
			@Nullable String localItemLocationCode;
		}

		@Serdeable
		@Value
		public static class Status {
			@Nullable String code;
			@Nullable String errorMessage;
		}

		@Serdeable
		@Value
		public static class LocalRequest {
			@Nullable String id;
			@Nullable String status;
			@Nullable String itemId;
			@Nullable String bibId;
		}

		@Serdeable
		@Value
		public static class Audit {
			@Nullable String id;
			@Nullable String patronRequestId;
			@Nullable String date;
			@Nullable String description;
			@Nullable PatronRequest.Status fromStatus;
			@Nullable PatronRequest.Status toStatus;
			@Nullable Map<String, Object> data;
		}
	}
}
