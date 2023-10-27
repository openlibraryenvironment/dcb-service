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

		// Workaround: records do not support been style properties
		public String getStatusCode() {
			if (getStatus() == null) {
				return null;
			}

			return getStatus().getCode();
		}

		@Serdeable
		@Value
		static class Citation {
			@Nullable UUID bibClusterId;
		}

		@Serdeable
		@Value
		static class Requestor {
			@Nullable String id;
			@Nullable String homeLibraryCode;
			@Nullable List<Identity> identities;
		}

		@Serdeable
		@Value
		static class Identity {
			@Nullable String localId;
			@Nullable String hostLmsCode;
			@Nullable Boolean homeIdentity;
		}

		@Serdeable
		@Value
		static class PickupLocation {
			@Nullable String code;
		}

		@Serdeable
		@Value
		static class SupplierRequest {
			@Nullable UUID id;
			@Nullable Item item;
			@Nullable String hostLmsCode;
			@Nullable String status;
			@Nullable String localHoldId;
			@Nullable String localHoldStatus;
		}

		@Serdeable
		@Value
		static class Item {
			@Nullable String id;
			@Nullable String localItemBarcode;
			@Nullable String localItemLocationCode;
		}

		@Serdeable
		@Value
		static class Status {
			@Nullable String code;
			@Nullable String errorMessage;
		}

		@Serdeable
		@Value
		static class LocalRequest {
			@Nullable String id;
			@Nullable String status;
			@Nullable String itemId;
			@Nullable String bibId;
		}

		@Serdeable
		@Value
		static class Audit {
			@Nullable String id;
			@Nullable String patronRequestId;
			@Nullable String date;
			@Nullable String description;
			PatronRequest.@Nullable Status fromStatus;
			PatronRequest.@Nullable Status toStatus;
			@Nullable Map<String, Object> data;
		}
	}
}
