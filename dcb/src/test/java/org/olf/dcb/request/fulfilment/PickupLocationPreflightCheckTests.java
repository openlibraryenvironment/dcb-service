package org.olf.dcb.request.fulfilment;

import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasProperty;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.olf.dcb.test.DcbTest;
import org.olf.dcb.test.LocationFixture;

import jakarta.inject.Inject;

@DcbTest
public class PickupLocationPreflightCheckTests {
	@Inject
	private PickupLocationPreflightCheck check;

	@Inject
	private LocationFixture locationFixture;

	@BeforeEach
	void beforeEach() {
		locationFixture.deleteAll();
	}

	@Test
	void shouldPassWhenPickupLocationCodeIsRecognised() {
		// Arrange
		locationFixture.createPickupLocation("Known Location", "known-pickup-location");

		// Act
		final var command = placeRequestCommand("known-pickup-location");

		// Assert
		assertThat(check(command), containsInAnyOrder(
			hasProperty("passed", is(true))));
	}

	@Test
	void shouldFailWhenPickupLocationCodeIsNotRecognised() {
		// Act
		final var command = placeRequestCommand("unknown-pickup-location");

		// Assert
		assertThat(check(command), containsInAnyOrder(allOf(
			hasProperty("passed", is(false)),
			hasProperty("failureDescription", is("\"unknown-pickup-location\" is not a recognised pickup location code"))
		)));
	}

	private List<CheckResult> check(PlacePatronRequestCommand command) {
		return check.check(command).block();
	}

	private static PlacePatronRequestCommand placeRequestCommand(String pickupLocationCode) {
		return PlacePatronRequestCommand.builder()
			.pickupLocation(PlacePatronRequestCommand.PickupLocation.builder()
				.code(pickupLocationCode)
				.build())
			.build();
	}
}
