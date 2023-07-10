package org.olf.dcb.core.interaction.sierra;

import static org.mockserver.model.JsonBody.json;

import java.util.List;

import org.mockserver.client.MockServerClient;

import io.micronaut.core.io.ResourceLoader;
import io.micronaut.serde.annotation.Serdeable;
import lombok.Builder;
import lombok.Data;

public class SierraPickupLocationsAPIFixture {
	private final MockServerClient mockServer;
	private final SierraMockServerRequests sierraMockServerRequests;
	private final SierraMockServerResponses sierraMockServerResponses;

	public SierraPickupLocationsAPIFixture(MockServerClient mockServer, ResourceLoader loader) {
		this.mockServer = mockServer;

		sierraMockServerRequests = new SierraMockServerRequests(
			"/iii/sierra-api/v6/branches/pickupLocations");

		sierraMockServerResponses = new SierraMockServerResponses(
			"classpath:mock-responses/sierra/", loader);
	}

	public void successfulResponseWhenGettingPickupLocations() {
		mockServer
			.when(sierraMockServerRequests.get())
			.respond(sierraMockServerResponses.jsonSuccess(json(List.of(
				pickupLocation("Almaden Branch", "10   "),
				pickupLocation("Alviso Branch", "18   "),
				pickupLocation("Bascom Branch", "24   ")))));
	}

	private static PickupLocation pickupLocation(String name, String code) {
		return PickupLocation.builder()
			.name(name)
			.code(code)
			.build();
	}

	@Data
	@Serdeable
	@Builder
	public static class PickupLocation {
		String name;
		String code;
	}
}
