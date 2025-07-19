package org.olf.dcb.request.fulfilment;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.olf.dcb.test.DcbTest;
import org.olf.dcb.test.LocationFixture;

import io.micronaut.context.annotation.Property;
import jakarta.inject.Inject;

@DcbTest
@Property(name = "dcb.requests.preflight-checks.pickup-location.enabled", value = "true")
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

		final var results = check(command);

		// Assert
		assertThat(results, containsInAnyOrder(passedCheck()));
	}

	@Test
	void shouldPassWhenPickupLocationCodeIsRecognisedAsAnID() {
		// Arrange
		final var location = locationFixture.createPickupLocation("Known Location", "known-pickup-location");

		// Act
		final var command = placeRequestCommand(location.getId().toString(),
			"pickup-context", "requester-host-lms-code");

		final var results = check(command);

		// Assert
		assertThat(results, containsInAnyOrder(passedCheck()));
	}

	@Test
	void shouldFailWhenPickupLocationCodeIsNotRecognised() {
		// Act
		final var command = placeRequestCommand("unknown-pickup-location",
			"pickup-context", "requester-host-lms-code");

		final var results = check(command);

		// Assert
		assertThat(results, containsInAnyOrder(
			failedCheck("UNKNOWN_PICKUP_LOCATION_CODE",
				"\"unknown-pickup-location\" is not a recognised pickup location code")
		));
	}

	private List<CheckResult> check(PlacePatronRequestCommand command) {
		return singleValueFrom(check.check(command));
	}
}
