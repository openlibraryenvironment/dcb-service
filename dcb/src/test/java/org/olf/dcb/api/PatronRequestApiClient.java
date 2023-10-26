package org.olf.dcb.api;

import java.util.UUID;

import org.olf.dcb.test.clients.LoginClient;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.inject.Singleton;
import net.minidev.json.JSONObject;

@Singleton
class PatronRequestApiClient {
	private final HttpClient httpClient;
	private final LoginClient loginClient;

	public PatronRequestApiClient(@Client("/") HttpClient client, LoginClient loginClient) {
		this.httpClient = client;
		this.loginClient = loginClient;
	}

	HttpResponse<PlacedPatronRequest> placePatronRequest(JSONObject json) {
		final var accessToken = loginClient.getAccessToken();

		final var blockingClient = httpClient.toBlocking();

		return blockingClient.exchange(
			HttpRequest.POST("/patrons/requests/place", json).bearerAuth(accessToken),
			PlacedPatronRequest.class);
	}

	HttpResponse<PlacedPatronRequest> placePatronRequest(UUID bibClusterId,
		String localId, String pickupLocationCode, String localSystemCode,
		String homeLibraryCode) {

		return placePatronRequest(createPlacePatronRequestCommand(bibClusterId,
			localId, pickupLocationCode, localSystemCode, homeLibraryCode));
	}

	private static JSONObject createPlacePatronRequestCommand(final UUID bibClusterId,
		final String localId, final String pickupLocationCode, final String localSystemCode,
		String homeLibraryCode) {

		return new JSONObject() {{
			put("citation", new JSONObject() {{ put("bibClusterId", bibClusterId.toString()); }} );
			put("requestor", new JSONObject() {{
				put("localId", localId);
				put("localSystemCode", localSystemCode);
				put("homeLibraryCode", homeLibraryCode);
			}});
			put("pickupLocation", new JSONObject() {{ put("code", pickupLocationCode); }} );
		}};
	}

	@Serdeable
	record PlacedPatronRequest(@Nullable UUID id, @Nullable Citation citation,
		@Nullable Requestor requestor, @Nullable PickupLocation pickupLocation,
		@Nullable Status status, @Nullable LocalRequest localRequest) {

		@Serdeable
		record Citation(@Nullable UUID bibClusterId) { }
		@Serdeable
		record Requestor(@Nullable String localId, @Nullable String localSystemCode,
			@Nullable String homeLibraryCode) { }

		@Serdeable
		record PickupLocation(@Nullable String code) { }
		@Serdeable
		record Status(@Nullable String code, @Nullable String errorMessage) { }
		@Serdeable
		record LocalRequest(@Nullable String id, @Nullable String status) { }
	}
}
