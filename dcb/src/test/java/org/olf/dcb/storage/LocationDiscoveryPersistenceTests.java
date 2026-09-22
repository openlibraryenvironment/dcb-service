package org.olf.dcb.storage;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.olf.dcb.core.model.Location;
import org.olf.dcb.core.model.LocationCreationSource;
import org.olf.dcb.core.svc.LocationService;
import org.olf.dcb.test.DcbTest;
import org.olf.dcb.test.HostLmsFixture;
import org.olf.dcb.test.LocationFixture;

import jakarta.inject.Inject;

@DcbTest
class LocationDiscoveryPersistenceTests {
	@Inject
	private LocationService locationService;

	@Inject
	private LocationRepository locationRepository;

	@Inject
	private HostLmsFixture hostLmsFixture;

	@Inject
	private LocationFixture locationFixture;

	@BeforeEach
	void beforeEach() {
		locationFixture.deleteAll();
	}

	@Test
	void availabilityDiscoveredLocationRetainsItsProvenance() {
		final var host = hostLmsFixture.createKohaHostLms(
			"location-discovery-provenance", "https://koha.example");

		final var created = singleValueFrom(locationService.memoize(
			Location.builder().code("BRANCH-NORTH").name("North Branch").build(),
			null,
			host));

		assertThat(created, is(notNullValue()));
		assertThat(created.getCreationSource(), is(LocationCreationSource.AVAILABILITY_DISCOVERY));

		final var persisted = singleValueFrom(
			locationRepository.findOneByHostSystemAndCode(host, "BRANCH-NORTH"));

		assertThat(persisted, is(notNullValue()));
		assertThat(persisted.getCreationSource(), is(LocationCreationSource.AVAILABILITY_DISCOVERY));
	}
}
