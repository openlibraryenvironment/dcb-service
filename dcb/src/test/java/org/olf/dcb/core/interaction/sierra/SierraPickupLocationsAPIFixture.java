package org.olf.dcb.core.interaction.sierra;

import java.util.List;

import org.olf.dcb.test.MockServer;

import io.micronaut.serde.annotation.Serdeable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@AllArgsConstructor
public class SierraPickupLocationsAPIFixture {
	private final MockServer mockServer;

	public void successfulResponseWhenGettingPickupLocations() {
		mockServer.mockGet("/iii/sierra-api/v6/branches/pickupLocations", List.of(
			pickupLocation("Almaden Branch", "10   "),
			pickupLocation("Alviso Branch", "18   "),
			pickupLocation("Bascom Branch", "24   ")));
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
