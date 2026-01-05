package org.olf.dcb.api;

import static org.olf.dcb.security.RoleNames.ADMINISTRATOR;

import java.util.List;
import java.util.UUID;

import org.olf.dcb.security.TestStaticTokenValidator;

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
		private static final String accessToken = "test-patreq-client-token";

	public PatronRequestApiClient(@Client("/") HttpClient client) {
		this.httpClient = client;

		TestStaticTokenValidator.add(accessToken, "test-patreq-client-token",
			List.of(ADMINISTRATOR));
	}

	HttpResponse<PlacedPatronRequest> placePatronRequest(UUID bibClusterId,
		String localId, String pickupLocationCode, String localSystemCode,
		String homeLibraryCode) {

		return placePatronRequest(PlacePatronRequestCommand.builder()
			.requestor(Requestor.builder()
				.localId(localId)
				.localSystemCode(localSystemCode)
				.homeLibraryCode(homeLibraryCode)
				.build())
			.citation(Citation.builder()
				.bibClusterId(bibClusterId)
				.build())
			.pickupLocation(PickupLocation.builder()
				.code(pickupLocationCode)
				.build())
			.build());
	}

	HttpResponse<PlacedPatronRequest> placePatronRequest(PlacePatronRequestCommand command) {
		final var blockingClient = httpClient.toBlocking();

		final var request = HttpRequest.POST("/patrons/requests/place", command)
			.bearerAuth(accessToken);

		return blockingClient.exchange(request, PlacedPatronRequest.class);
	}

	HttpResponse<UUID> updatePatronRequest(UUID patronRequestId) {
		final var blockingClient = httpClient.toBlocking();

		final var request = HttpRequest.POST("/patrons/requests/" + patronRequestId + "/update", patronRequestId)
			.bearerAuth(accessToken);

		return blockingClient.exchange(request, UUID.class);
	}

	HttpResponse<UUID> rollbackPatronRequest(UUID patronRequestId) {
		final var blockingClient = httpClient.toBlocking();

		final var request = HttpRequest.POST("/patrons/requests/" + patronRequestId + "/rollback", patronRequestId)
			.bearerAuth(accessToken);

		return blockingClient.exchange(request, UUID.class);
	}

	public void removeTokenFromValidTokens() {
			TestStaticTokenValidator.invalidateToken(accessToken);
	}

	@Serdeable
	@Value
	@Builder
	public static class PlacePatronRequestCommand {
		@Nullable Citation citation;
		@Nullable Requestor requestor;
		@Nullable PickupLocation pickupLocation;
		@Nullable Item item;
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

	@Serdeable
	@Value
	@Builder
	public static class Item {
		@Nullable String localId;
		@Nullable String localSystemCode;	// host LMS code
		@Nullable String agencyCode;
	}
}
