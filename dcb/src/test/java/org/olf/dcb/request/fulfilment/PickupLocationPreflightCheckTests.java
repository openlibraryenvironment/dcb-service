package org.olf.dcb.request.fulfilment;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.olf.dcb.test.DcbTest;
import org.olf.dcb.test.LocationFixture;

import jakarta.inject.Inject;

@DcbTest
public class PickupLocationPreflightCheckTests extends AbstractPreflightCheckTests {
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
		final var command = placeRequestCommand("known-pickup-location",
			"pickup-context", "requester-host-lms-code");

		final var results = check.check(command).block();

		// Assert
		assertThat(results, containsInAnyOrder(passedCheck()));
	}

	@Test
	void shouldFailWhenPickupLocationCodeIsNotRecognised() {
		// Act
		final var command = placeRequestCommand("unknown-pickup-location",
			"pickup-context", "requester-host-lms-code");

		final var results = check.check(command).block();

		// Assert
		assertThat(results, containsInAnyOrder(
			failedCheck("\"unknown-pickup-location\" is not a recognised pickup location code")
		));
	}
}
