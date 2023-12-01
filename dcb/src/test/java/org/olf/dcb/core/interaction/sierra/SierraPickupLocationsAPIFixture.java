package org.olf.dcb.core.interaction.sierra;

import static org.mockserver.model.JsonBody.json;

import java.util.List;

import org.mockserver.client.MockServerClient;
import org.olf.dcb.test.TestResourceLoaderProvider;

import io.micronaut.serde.annotation.Serdeable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@AllArgsConstructor
public class SierraPickupLocationsAPIFixture {
	private final MockServerClient mockServer;
	private final SierraMockServerRequests sierraMockServerRequests;
	private final SierraMockServerResponses sierraMockServerResponses;

	public SierraPickupLocationsAPIFixture(MockServerClient mockServer,
		TestResourceLoaderProvider testResourceLoaderProvider) {

		this(mockServer, new SierraMockServerRequests("/iii/sierra-api/v6/branches/pickupLocations"),
			new SierraMockServerResponses(
				testResourceLoaderProvider.forBasePath("classpath:mock-responses/sierra/")));
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
