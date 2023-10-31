package org.olf.dcb.request.fulfilment;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.olf.dcb.test.AgencyFixture;
import org.olf.dcb.test.DcbTest;
import org.olf.dcb.test.ReferenceValueMappingFixture;

import jakarta.inject.Inject;

@DcbTest
public class PickupLocationToAgencyMappingPreflightCheckTests extends AbstractPreflightCheckTests {
	@Inject
	private PickupLocationToAgencyMappingPreflightCheck check;

	@Inject
	private ReferenceValueMappingFixture referenceValueMappingFixture;
	@Inject
	private AgencyFixture agencyFixture;

	@BeforeEach
	void beforeEach() {
		referenceValueMappingFixture.deleteAll();
		agencyFixture.deleteAll();
	}

	@Test
	void shouldPassWhenPickupLocationIsMappedToAnAgency() {
		// Arrange
		referenceValueMappingFixture.definePickupLocationToAgencyMapping("known-pickup-location", "any-agency");

		// Act
		final var command = placeRequestCommand("known-pickup-location");

		final var results = check.check(command).block();

		// Assert
		assertThat(results, containsInAnyOrder(passedCheck()));
	}

	@Test
	void shouldFailWhenPickupLocationCodeIsNotMappedToAnAgency() {
		// Act
		final var command = placeRequestCommand("known-pickup-location");

		final var results = check.check(command).block();

		// Assert
		assertThat(results, containsInAnyOrder(
			failedCheck("\"known-pickup-location\" is not mapped to an agency")));
	}
}
