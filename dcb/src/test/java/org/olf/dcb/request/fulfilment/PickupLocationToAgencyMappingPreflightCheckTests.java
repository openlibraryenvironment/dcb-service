package org.olf.dcb.request.fulfilment;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.olf.dcb.test.AgencyFixture;
import org.olf.dcb.test.DcbTest;
import org.olf.dcb.test.HostLmsFixture;
import org.olf.dcb.test.LocationFixture;
import org.olf.dcb.test.ReferenceValueMappingFixture;

import io.micronaut.context.annotation.Property;
import jakarta.inject.Inject;

@DcbTest
@Property(name = "dcb.requests.preflight-checks.pickup-location-to-agency-mapping.enabled", value = "true")
class PickupLocationToAgencyMappingPreflightCheckTests extends AbstractPreflightCheckTests {
	@Inject
	private PickupLocationToAgencyMappingPreflightCheck check;

	@Inject
	private ReferenceValueMappingFixture referenceValueMappingFixture;
	@Inject
	private AgencyFixture agencyFixture;
	@Inject
	private LocationFixture locationFixture;
	@Inject
	private HostLmsFixture hostLmsFixture;

	@BeforeEach
	void beforeEach() {
		referenceValueMappingFixture.deleteAll();
		locationFixture.deleteAll();
		agencyFixture.deleteAll();
		locationFixture.deleteAll();
	}

	@Test
	void shouldPassWhenPickupLocationIsMappedToAnAgencyInDcbContext() {
		// Arrange
    final var hostLms = hostLmsFixture.createSierraHostLms("TESTHOST", "1234", "5678", "http://nowhere.com/");

    final var agency = agencyFixture.defineAgency("test-agency", "Test Agency", hostLms);

    // Agency has 1 PICKUP location of PICKUP_LOCATION_CODE
    locationFixture.createPickupLocation(UUID.fromString("0f102b5a-e300-41c8-9aca-afd170e17921"),
			"PickupLocationName", "PickupLocationCode", agency);

		// Act
		final var command = placeRequestCommand("0f102b5a-e300-41c8-9aca-afd170e17921",
			null, "requester-host-lms-code");

		final var results = check(command);

		// Assert
		assertThat(results, containsInAnyOrder(passedCheck()));
	}

	@Test
	void shouldFailWhenPickupLocationIsNotMappedToAnAgency() {
		// Act
		final var command = placeRequestCommand("known-pickup-location",
			"pickup-context", "requester-host-lms-code");

		final var results = check(command);

		// Assert
		assertThat(results, containsInAnyOrder(
			failedCheck("PICKUP_LOCATION_NOT_MAPPED_TO_AGENCY",
				"Pickup location \"known-pickup-location\" is not mapped to an agency")
		));
	}

	private List<CheckResult> check(PlacePatronRequestCommand command) {
		return singleValueFrom(check.check(command));
	}
}
