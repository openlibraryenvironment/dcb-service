package org.olf.dcb.request.fulfilment;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.olf.dcb.test.AgencyFixture;
import org.olf.dcb.test.DcbTest;
import org.olf.dcb.test.LocationFixture;
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
	@Inject
	private LocationFixture locationFixture;

	@BeforeEach
	void beforeEach() {
		referenceValueMappingFixture.deleteAll();
		agencyFixture.deleteAll();
		locationFixture.deleteAll();
	}

	@Test
	void shouldPassWhenPickupLocationIsMappedToAnAgencyInDcbContext() {
		// Arrange
		agencyFixture.defineAgency("known-agency");

		definePickupLocationToAgencyMapping("DCB", "known-pickup-location", "known-agency");

		// Act
		final var command = placeRequestCommand("known-pickup-location",
			"pickup-context", "requester-host-lms-code");

		final var results = check.check(command).block();

		// Assert
		assertThat(results, containsInAnyOrder(passedCheck()));
	}

	@Test
	void shouldPassWhenPickupLocationIsMappedToAnAgencyInExplicitContext() {
		// Arrange
		agencyFixture.defineAgency("known-agency");

		definePickupLocationToAgencyMapping("pickup-context", "known-pickup-location", "known-agency");

		// Act
		final var command = placeRequestCommand("known-pickup-location",
			"pickup-context", "requester-host-lms-code");

		final var results = check.check(command).block();

		// Assert
		assertThat(results, containsInAnyOrder(passedCheck()));
	}

	@Test
	void shouldPassWhenPickupLocationIsMappedToAnAgencyInRequestorHostLmsContext() {
		// Arrange
		agencyFixture.defineAgency("known-agency");

		definePickupLocationToAgencyMapping("requester-host-lms-code", "known-pickup-location", "known-agency");

		// Act
		final var command = placeRequestCommand("known-pickup-location",
			null, "requester-host-lms-code");

		final var results = check.check(command).block();

		// Assert
		assertThat(results, containsInAnyOrder(passedCheck()));
	}

	@Test
	void shouldFailWhenPickupLocationIsNotMappedToAnAgency() {
		// Act
		final var command = placeRequestCommand("known-pickup-location",
			"pickup-context", "requester-host-lms-code");

		final var results = check.check(command).block();

		// Assert
		assertThat(results, containsInAnyOrder(
			failedCheck("\"known-pickup-location\" is not mapped to an agency")));
	}

	@Test
	void shouldFailWhenPickupLocationIsMappedToUnrecognisedAgency() {
		// Arrange
		definePickupLocationToAgencyMapping("DCB", "pickup-location", "unknown-agency");

		// Act
		final var command = placeRequestCommand("pickup-location",
			"pickup-context", "requester-host-lms-code");

		final var results = check.check(command).block();

		// Assert
		assertThat(results, containsInAnyOrder(
			failedCheck("\"pickup-location\" is mapped to \"unknown-agency\" which is not a recognised agency")));
	}

	private void definePickupLocationToAgencyMapping(String fromContext, String locationCode, String agencyCode) {
		referenceValueMappingFixture.defineLocationToAgencyMapping(fromContext, locationCode, agencyCode);
	}
}
