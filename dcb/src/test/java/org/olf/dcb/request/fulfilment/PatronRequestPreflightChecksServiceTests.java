package org.olf.dcb.request.fulfilment;

import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasProperty;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;

import java.util.UUID;

import org.hamcrest.Matcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.olf.dcb.test.DcbTest;
import org.olf.dcb.test.LocationFixture;
import org.olf.dcb.test.ReferenceValueMappingFixture;

import jakarta.inject.Inject;

@DcbTest
public class PatronRequestPreflightChecksServiceTests {
	@Inject
	private PatronRequestPreflightChecksService preflightChecksService;

	@Inject
	private LocationFixture locationFixture;
	@Inject
	private ReferenceValueMappingFixture referenceValueMappingFixture;

	@BeforeEach
	void beforeEach() {
		referenceValueMappingFixture.deleteAll();
		locationFixture.deleteAll();
	}

	@Nested
	class RecognisedPickupLocation {
		@Test
		void shouldPassWhenPickupLocationCodeIsRecognised() {
			// Arrange
			locationFixture.createPickupLocation("Known Location", "known-pickup-location");

			referenceValueMappingFixture.definePickupLocationToAgencyMapping("known-pickup-location", "any-agency");

			// Act
			final var command = placeRequestCommand("known-pickup-location");

			// Assert

			// Should return the command used as input to allow for easy chaining
			assertThat(check(command), is(command));
		}

		@Test
		void shouldFailWhenPickupLocationCodeIsNotRecognised() {
			// Arrange
			referenceValueMappingFixture.definePickupLocationToAgencyMapping("unknown-pickup-location", "any-agency");

			// Act
			final var command = placeRequestCommand("unknown-pickup-location");

			final var exception = assertThrows(PreflightCheckFailedException.class,
				() -> check(command));

			// Assert
			assertThat(exception, hasProperty("failedChecks", containsInAnyOrder(
				hasFailedCheck("\"unknown-pickup-location\" is not a recognised pickup location code")
			)));
		}
	}

	@Nested
	class PickupLocationToAgencyMapping {
		@Test
		void shouldPassWhenPickupLocationIsMappedToAnAgency() {
			// Arrange
			locationFixture.createPickupLocation("Known Location", "known-pickup-location");

			referenceValueMappingFixture.definePickupLocationToAgencyMapping("known-pickup-location", "any-agency");

			// Act
			final var command = placeRequestCommand("known-pickup-location");

			// Assert

			// Should return the command used as input to allow for easy chaining
			assertThat(check(command), is(command));
		}

		@Test
		void shouldFailWhenPickupLocationCodeIsNotMappedToAnAgency() {
			// Act
			locationFixture.createPickupLocation("Known Location", "known-pickup-location");

			final var command = placeRequestCommand("known-pickup-location");

			final var exception = assertThrows(PreflightCheckFailedException.class,
				() -> check(command));

			// Assert
			assertThat(exception, hasProperty("failedChecks", containsInAnyOrder(
				hasFailedCheck("\"known-pickup-location\" is not mapped to an agency")
			)));
		}
	}

	private PlacePatronRequestCommand check(PlacePatronRequestCommand command) {
		return preflightChecksService.check(command).block();
	}

	private static PlacePatronRequestCommand placeRequestCommand(String pickupLocationCode) {
		return PlacePatronRequestCommand.builder()
			.pickupLocation(PlacePatronRequestCommand.PickupLocation.builder()
				.code(pickupLocationCode)
				.build())
			.build();
	}

	private static Matcher<PreflightCheckFailedException> hasFailedCheck(
		String expectedFailureDescription) {

		return allOf(hasProperty("failureDescription", is(expectedFailureDescription)));
	}
}
