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
		String requestorIdentifier, String agencyCode, String pickupLocationCode) {

		return placePatronRequest(createPlacePatronRequestCommand(bibClusterId,
			requestorIdentifier, agencyCode, pickupLocationCode));
	}

	private static JSONObject createPlacePatronRequestCommand(
		final UUID bibClusterId, final String requestorIdentifier,
		final String agencyCode, final String pickupLocationCode) {

		return new JSONObject() {{
			// citation
			final var citation = new JSONObject() {{
				put("bibClusterId", bibClusterId.toString());
			}};
			put("citation", citation);
			// requestor
			final var requestor = new JSONObject() {{
				put("identifier", requestorIdentifier);
				final var agency = new JSONObject() {{
					put("code", agencyCode);
				}};
				put("agency", agency);
			}};
			put("requestor", requestor);
			// pickup location
			final var pickupLocation = new JSONObject() {{
				put("code", pickupLocationCode);
			}};
			put("pickupLocation", pickupLocation);
		}};
	}

	@Serdeable
	static
	record PlacedPatronRequest(@Nullable UUID id, @Nullable Citation citation,
		@Nullable Requestor requestor, @Nullable PickupLocation pickupLocation) {

		@Serdeable
		record Citation(@Nullable UUID bibClusterId) { }

		@Serdeable
		record Requestor(String identifier, @Nullable Agency agency) { }

		@Serdeable
		record Agency(String code) { }

		@Serdeable
		record PickupLocation(String code) { }
	}
}
