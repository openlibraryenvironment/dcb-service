package org.olf.dcb.core.svc;

import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;
import static org.olf.dcb.test.matchers.ItemMatchers.hasAgencyCode;
import static org.olf.dcb.test.matchers.ItemMatchers.hasAgencyName;
import static org.olf.dcb.test.matchers.ItemMatchers.hasHostLmsCode;
import static org.olf.dcb.test.matchers.ItemMatchers.hasNoAgency;
import static org.olf.dcb.test.matchers.ItemMatchers.hasNoHostLmsCode;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.olf.dcb.core.model.Item;
import org.olf.dcb.core.model.Location;
import org.olf.dcb.request.workflow.exceptions.UnableToResolveAgencyProblem;
import org.olf.dcb.test.AgencyFixture;
import org.olf.dcb.test.DcbTest;
import org.olf.dcb.test.HostLmsFixture;
import org.olf.dcb.test.ReferenceValueMappingFixture;

import jakarta.inject.Inject;

@DcbTest
class LocationToAgencyMappingServiceTests {
	private static final String CATALOGUING_HOST_LMS_CODE = "cataloguing-host-lms";

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
		final var circulatingHostLmsCode = "circulating-host-lms";

		final var circulatingHostLms = hostLmsFixture.createSierraHostLms(
			circulatingHostLmsCode, "some-user",
			"some-password", "https://some-address");

		final var agencyCode = "known-agency";

		agencyFixture.defineAgency(agencyCode, "Known agency", circulatingHostLms);

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
		assertThat(enrichedItem, hasHostLmsCode(circulatingHostLmsCode));
	}

	@Test
	void shouldTolerateAbsenceOfMappingWhenEnrichingItemWithAgency() {
		// Act
		final var item = exampleItem(Location.builder()
			.code("location-with-no-mapping")
			.build());

		final var enrichedItem = enrichItemWithAgency(item);

		// Assert
		assertThat(enrichedItem, allOf(
			notNullValue(),
			hasNoAgency(),
			hasNoHostLmsCode()
		));
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
		assertThat(enrichedItem, allOf(
			notNullValue(),
			hasNoAgency(),
			hasNoHostLmsCode()
		));
	}

	@Test
	void shouldTolerateAgencyWithoutHostLmsWhenEnrichingItemWithAgency() {
		// Arrange
		final var agencyCode = "known-agency";

		agencyFixture.defineAgencyWithNoHostLms(agencyCode, "Known agency");

		final var locationCode = "location-with-mapping";

		referenceValueMappingFixture.defineLocationToAgencyMapping(CATALOGUING_HOST_LMS_CODE,
			locationCode, agencyCode);

		// Act
		final var item = exampleItem(Location.builder()
			.code(locationCode)
			.build());

		final var enrichedItem = enrichItemWithAgency(item);

		// Assert
		assertThat(enrichedItem, allOf(
			notNullValue(),
			hasAgencyCode(agencyCode),
			hasAgencyName("Known agency"),
			hasNoHostLmsCode()
		));
	}

	@Test
	void shouldTolerateNullLocationWhenEnrichingItemWithAgency() {
		// Act
		final var itemWithNullLocation = exampleItem(null);

		final var enrichedItem = enrichItemWithAgency(itemWithNullLocation);

		// Assert
		assertThat(enrichedItem, allOf(
			notNullValue(),
			hasNoAgency(),
			hasNoHostLmsCode()
		));
	}

	@Test
	void shouldTolerateNullLocationCodeWhenEnrichingItemWithAgency() {
		// Act
		final var itemWithNullLocationCode = exampleItem(
			Location.builder()
				.code(null)
				.build());

		final var enrichedItem = enrichItemWithAgency(itemWithNullLocationCode);

		// Assert
		assertThat(enrichedItem, allOf(
			notNullValue(),
			hasNoAgency(),
			hasNoHostLmsCode()
		));
	}

	@Test
	void shouldFallBackToTheDefaultAgencyOnADedicatedSystem() {
		final var hostLmsCode = "dedicated-koha";

		final var hostLms = hostLmsFixture.createKohaHostLms(hostLmsCode,
			"https://dedicated-koha.example.com",
			Map.of("default-agency-code", "the-only-library"));

		agencyFixture.defineAgency("the-only-library", "The only library", hostLms);

		final var agency = singleValueFrom(locationToAgencyMappingService
			.resolveAgencyForPatronHomeLocation(hostLmsCode, "unmapped-branch"));

		assertThat("A system serving one library can stand in for an unmapped location",
			agency.getCode(), is("the-only-library"));
	}

	@Test
	void shouldNotFallBackToTheDefaultAgencyOnASharedSystem() {
		// The whole point of the flag. Standing one agency in for an unrecognised
		// location on a system hosting sixty libraries attributes every co-tenant's
		// patrons - including libraries outside the consortium entirely - to whichever
		// agency happened to be configured, and says nothing about having done it.
		final var hostLmsCode = "shared-koha";

		final var hostLms = hostLmsFixture.createKohaHostLms(hostLmsCode,
			"https://shared-koha.example.com",
			Map.of("shared-system", true, "default-agency-code", "one-co-tenant"));

		agencyFixture.defineAgency("one-co-tenant", "One co-tenant library", hostLms);

		assertThat("The default agency must not be usable on a shared system",
			singleValueFrom(locationToAgencyMappingService.findDefaultAgencyCode(hostLmsCode)),
			is(nullValue()));

		assertThrows(UnableToResolveAgencyProblem.class, () -> singleValueFrom(
			locationToAgencyMappingService.resolveAgencyForPatronHomeLocation(
				hostLmsCode, "unmapped-branch")));
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
