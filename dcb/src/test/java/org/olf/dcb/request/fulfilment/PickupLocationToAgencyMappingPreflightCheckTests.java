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
import org.olf.dcb.test.ReferenceValueMappingFixture;

import jakarta.inject.Inject;

@DcbTest
public class PickupLocationToAgencyMappingPreflightCheckTests {
	@Inject
	private PickupLocationToAgencyMappingPreflightCheck check;

	@Inject
	private ReferenceValueMappingFixture referenceValueMappingFixture;

	@BeforeEach
	void beforeEach() {
		referenceValueMappingFixture.deleteAll();
	}

	@Test
	void shouldPassWhenPickupLocationIsMappedToAnAgency() {
		// Arrange
		referenceValueMappingFixture.definePickupLocationToAgencyMapping("known-pickup-location", "any-agency");

		// Act
		final var command = placeRequestCommand("known-pickup-location");

		// Assert
		assertThat(check(command), containsInAnyOrder(
			hasProperty("passed", is(true))));
	}

	@Test
	void shouldFailWhenPickupLocationCodeIsNotMappedToAnAgency() {
		// Act
		final var command = placeRequestCommand("known-pickup-location");

		// Assert
		assertThat(check(command), containsInAnyOrder(allOf(
			hasProperty("passed", is(false)),
			hasProperty("failureDescription", is("\"known-pickup-location\" is not mapped to an agency"))
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
