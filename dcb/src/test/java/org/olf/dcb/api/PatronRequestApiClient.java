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
import lombok.Builder;
import lombok.Value;

@Singleton
class PatronRequestApiClient {
	private final HttpClient httpClient;
	private final LoginClient loginClient;

	public PatronRequestApiClient(@Client("/") HttpClient client, LoginClient loginClient) {
		this.httpClient = client;
		this.loginClient = loginClient;
	}

	HttpResponse<PlacedPatronRequest> placePatronRequest(UUID bibClusterId,
		String localId, String pickupLocationCode, String localSystemCode,
		String homeLibraryCode) {
		return placePatronRequest(bibClusterId,localId,pickupLocationCode,localSystemCode,homeLibraryCode,null);
	}

	HttpResponse<PlacedPatronRequest> placePatronRequest(UUID bibClusterId,
		String localId, String pickupLocationCode, String localSystemCode,
		String homeLibraryCode, String volumeDesignation) {

		return placePatronRequest(PlacePatronRequestCommand.builder()
			.requestor(Requestor.builder()
				.localId(localId)
				.localSystemCode(localSystemCode)
				.homeLibraryCode(homeLibraryCode)
				.build())
			.citation(Citation.builder()
				.bibClusterId(bibClusterId)
				.volumeDesignator(volumeDesignation)
				.build())
			.pickupLocation(PickupLocation.builder()
				.code(pickupLocationCode)
				.build())
			.build());
	}

	HttpResponse<PlacedPatronRequest> placePatronRequest(PlacePatronRequestCommand command) {
		final var accessToken = loginClient.getAccessToken();

		final var blockingClient = httpClient.toBlocking();

		final var request = HttpRequest.POST("/patrons/requests/place", command)
			.bearerAuth(accessToken);

		return blockingClient.exchange(request, PlacedPatronRequest.class);
	}

	@Serdeable
	@Value
	@Builder
	public static class PlacePatronRequestCommand {
		@Nullable Citation citation;
		@Nullable Requestor requestor;
		@Nullable PickupLocation pickupLocation;
	}

	@Serdeable
	@Value
	public static class PlacedPatronRequest {
		@Nullable UUID id;
		@Nullable Citation citation;
		@Nullable Requestor requestor;
		@Nullable PickupLocation pickupLocation;
		@Nullable Status status;
		@Nullable LocalRequest localRequest;

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
		}
	}

	@Serdeable
	@Value
	@Builder
	public static class Citation {
		@Nullable UUID bibClusterId;
		@Nullable String volumeDesignator;
	}

	@Serdeable
	@Value
	@Builder
	public static class Requestor {
		@Nullable String localId;
		@Nullable String localSystemCode;
		@Nullable String homeLibraryCode;
	}

	@Serdeable
	@Value
	@Builder
	public static class PickupLocation {
		@Nullable String code;
	}
}
