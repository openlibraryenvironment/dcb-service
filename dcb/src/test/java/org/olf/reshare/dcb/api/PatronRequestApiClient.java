package org.olf.reshare.dcb.api;

import java.util.UUID;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.inject.Inject;
import net.minidev.json.JSONObject;

class PatronRequestApiClient {
	@Inject
	@Client("/")
	HttpClient client;

	HttpResponse<PlacedPatronRequest> placePatronRequest(JSONObject json) {
		return client.toBlocking()
			.exchange(HttpRequest.POST("/patrons/requests/place", json),
				PlacedPatronRequest.class);
	}

	HttpResponse<PlacedPatronRequest> placePatronRequest(UUID bibClusterId,
		String localId, String agencyCode, String pickupLocationCode, String localSystemCode) {

		return placePatronRequest(createPlacePatronRequestCommand(bibClusterId,
			localId, agencyCode, pickupLocationCode, localSystemCode));
	}

	private static JSONObject createPlacePatronRequestCommand(
		final UUID bibClusterId, final String localId,
		final String agencyCode, final String pickupLocationCode,
		final String localSystemCode) {
		return new JSONObject() {{
			put("citation", new JSONObject() {{ put("bibClusterId", bibClusterId.toString()); }} );
			put("requestor", new JSONObject() {{
				put("localId", localId);
				put("agency", new JSONObject() {{ put("code", agencyCode); }} );
				put("localSystemCode", localSystemCode); }});
			put("pickupLocation", new JSONObject() {{ put("code", pickupLocationCode); }} );
		}};
	}

	@Serdeable
	record PlacedPatronRequest(UUID id, Citation citation, Requestor requestor, PickupLocation pickupLocation, @Nullable Status status) {
		@Serdeable
		record Citation(UUID bibClusterId) { }
		@Serdeable
		record Requestor(String localId, String localSystemCode, Agency agency) { }
		@Serdeable
		record Agency(String code) { }
		@Serdeable
		record PickupLocation(String code) { }
		@Serdeable
		record Status(String code) { }
	}
}
