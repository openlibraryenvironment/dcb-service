package org.olf.dcb.core.svc;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.olf.dcb.test.matchers.ItemMatchers.hasNoAgencyCode;
import static org.olf.dcb.test.matchers.ItemMatchers.hasNoAgencyName;

import org.junit.jupiter.api.Test;
import org.olf.dcb.core.model.Item;
import org.olf.dcb.core.model.Location;
import org.olf.dcb.test.DcbTest;

import jakarta.inject.Inject;

@DcbTest
class LocationToAgencyMappingServiceTests {
	@Inject
	private LocationToAgencyMappingService locationToAgencyMappingService;

	@Test
	void shouldTolerateNullLocationWhenEnrichingItemWithAgency() {
		// Act
		final var itemWithNullLocation = exampleItem(null);

		final var enrichedItem = locationToAgencyMappingService.enrichItemAgencyFromLocation(
				itemWithNullLocation, "host-lms")
			.block();

		// Assert
		assertThat(enrichedItem, hasNoAgencyCode());
		assertThat(enrichedItem, hasNoAgencyName());
	}

	@Test
	void shouldTolerateNullLocationCodeWhenEnrichingItemWithAgency() {
		// Act
		final var itemWithNullLocationCode = exampleItem(Location.builder()
			.code(null)
			.build());

		final var enrichedItem = locationToAgencyMappingService.enrichItemAgencyFromLocation(
				itemWithNullLocationCode, "host-lms")
			.block();

		// Assert
		assertThat(enrichedItem, hasNoAgencyCode());
		assertThat(enrichedItem, hasNoAgencyName());
	}

	private static Item exampleItem(Location location) {
		return Item.builder()
			.location(location)
			.build();
	}
}
