package org.olf.dcb.core.svc;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;
import static org.olf.dcb.test.matchers.ItemMatchers.hasAgencyCode;
import static org.olf.dcb.test.matchers.ItemMatchers.hasAgencyName;
import static org.olf.dcb.test.matchers.ItemMatchers.hasNoAgencyCode;
import static org.olf.dcb.test.matchers.ItemMatchers.hasNoAgencyName;
import static org.olf.dcb.test.matchers.ItemMatchers.hasNoHostLmsCode;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.olf.dcb.core.model.Item;
import org.olf.dcb.core.model.Location;
import org.olf.dcb.test.AgencyFixture;
import org.olf.dcb.test.DcbTest;
import org.olf.dcb.test.HostLmsFixture;
import org.olf.dcb.test.ReferenceValueMappingFixture;

import jakarta.inject.Inject;

@DcbTest
class LocationToAgencyMappingServiceTests {
	private final String CATALOGUING_HOST_LMS_CODE = "cataloguing-host-lms";
	private final String CIRCULATING_HOST_LMS_CODE = "circulating-host-lms";

	@Inject
	private LocationToAgencyMappingService locationToAgencyMappingService;

	@Inject
	private ReferenceValueMappingFixture referenceValueMappingFixture;
	@Inject
	private AgencyFixture agencyFixture;
	@Inject
	private HostLmsFixture hostLmsFixture;

	@BeforeEach
	void beforeEach() {
		referenceValueMappingFixture.deleteAll();
		hostLmsFixture.deleteAll();
		agencyFixture.deleteAll();
	}

	@Test
	void shouldEnrichItemWithAgencyMappedFromLocationCode() {
		// Arrange
		final var agencyCode = "known-agency";

		final var cataloguingHostLms = hostLmsFixture.createSierraHostLms(
			CATALOGUING_HOST_LMS_CODE, "some-user",
			"some-password", "https://some-address");

		agencyFixture.defineAgency(agencyCode, "Known agency", cataloguingHostLms);

		final var locationCode = "location-with-mapping";

		referenceValueMappingFixture.defineLocationToAgencyMapping(CATALOGUING_HOST_LMS_CODE,
			locationCode, agencyCode);

		// Act
		final var item = exampleItem(Location.builder()
			.code(locationCode)
			.build());

		final var enrichedItem = enrichItemWithAgency(item);

		// Assert
		assertThat(enrichedItem, hasAgencyCode(agencyCode));
		assertThat(enrichedItem, hasAgencyName("Known agency"));
		assertThat(enrichedItem, hasNoHostLmsCode());
	}

	@Test
	void shouldTolerateAbsenceOfMappingWhenEnrichingItemWithAgency() {
		// Act
		final var item = exampleItem(Location.builder()
			.code("location-with-no-mapping")
			.build());

		final var enrichedItem = enrichItemWithAgency(item);

		// Assert
		assertThat(enrichedItem, hasNoAgencyCode());
		assertThat(enrichedItem, hasNoAgencyName());
		assertThat(enrichedItem, hasNoHostLmsCode());
	}

	@Test
	void shouldTolerateMissingAgencyWhenEnrichingItemWithAgency() {
		// Act
		referenceValueMappingFixture.defineLocationToAgencyMapping("unknown-host-lms",
			"location-with-mapping", "unknown-agency");

		final var item = exampleItem(Location.builder()
			.code("location-with-mapping")
			.build());

		final var enrichedItem = enrichItemWithAgency(item);

		// Assert
		assertThat(enrichedItem, hasNoAgencyCode());
		assertThat(enrichedItem, hasNoAgencyName());
		assertThat(enrichedItem, hasNoHostLmsCode());
	}

	@Test
	void shouldTolerateNullLocationWhenEnrichingItemWithAgency() {
		// Act
		final var itemWithNullLocation = exampleItem(null);

		final var enrichedItem = enrichItemWithAgency(itemWithNullLocation
		);

		// Assert
		assertThat(enrichedItem, hasNoAgencyCode());
		assertThat(enrichedItem, hasNoAgencyName());
		assertThat(enrichedItem, hasNoHostLmsCode());
	}

	@Test
	void shouldTolerateNullLocationCodeWhenEnrichingItemWithAgency() {
		// Act
		final var itemWithNullLocationCode = exampleItem(Location.builder()
			.code(null)
			.build());

		final var enrichedItem = enrichItemWithAgency(itemWithNullLocationCode);

		// Assert
		assertThat(enrichedItem, hasNoAgencyCode());
		assertThat(enrichedItem, hasNoAgencyName());
		assertThat(enrichedItem, hasNoHostLmsCode());
	}

	private static Item exampleItem(Location location) {
		return Item.builder()
			.location(location)
			.build();
	}

	private Item enrichItemWithAgency(Item item) {
		return singleValueFrom(locationToAgencyMappingService
			.enrichItemAgencyFromLocation(item, CATALOGUING_HOST_LMS_CODE));
	}
}
