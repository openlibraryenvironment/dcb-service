package org.olf.dcb.item.availability;

import static java.util.Collections.emptyList;
import static java.util.UUID.randomUUID;
import static java.util.stream.Collectors.groupingBy;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;
import static org.olf.dcb.test.matchers.AvailabilityReportMatchers.hasError;
import static org.olf.dcb.test.matchers.AvailabilityReportMatchers.hasItems;
import static org.olf.dcb.test.matchers.AvailabilityReportMatchers.hasItemsInOrder;
import static org.olf.dcb.test.matchers.AvailabilityReportMatchers.hasNoErrors;
import static org.olf.dcb.test.matchers.AvailabilityReportMatchers.hasNoItems;
import static org.olf.dcb.test.matchers.ItemMatchers.hasBarcode;
import static org.olf.dcb.test.matchers.ItemMatchers.hasSourceHostLmsCode;
import static org.olf.dcb.test.matchers.ThrowableMatchers.hasMessage;
import static org.olf.dcb.utils.PropertyAccessUtils.getValue;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockserver.client.MockServerClient;
import org.olf.dcb.core.UnknownHostLmsException;
import org.olf.dcb.core.clustering.model.ClusterRecord;
import org.olf.dcb.core.interaction.sierra.SierraApiFixtureProvider;
import org.olf.dcb.core.interaction.sierra.SierraItem;
import org.olf.dcb.core.interaction.sierra.SierraItemsAPIFixture;
import org.olf.dcb.core.model.DataAgency;
import org.olf.dcb.core.model.DataHostLms;
import org.olf.dcb.core.model.Item;
import org.olf.dcb.request.resolution.CannotFindClusterRecordException;
import org.olf.dcb.test.AgencyFixture;
import org.olf.dcb.test.BibRecordFixture;
import org.olf.dcb.test.ClusterRecordFixture;
import org.olf.dcb.test.HostLmsFixture;
import org.olf.dcb.test.ReferenceValueMappingFixture;

import jakarta.inject.Inject;
import lombok.SneakyThrows;
import services.k_int.interaction.sierra.SierraTestUtils;
import services.k_int.test.mockserver.MockServerMicronautTest;

@MockServerMicronautTest
@TestInstance(PER_CLASS)
class LiveAvailabilityServiceTests {
	@Inject
	private SierraApiFixtureProvider sierraApiFixtureProvider;

	@Inject
	private ClusterRecordFixture clusterRecordFixture;
	@Inject
	private BibRecordFixture bibRecordFixture;
	@Inject
	private HostLmsFixture hostLmsFixture;
	@Inject
	private ReferenceValueMappingFixture referenceValueMappingFixture;
	@Inject
	private AgencyFixture agencyFixture;

	@Inject
	private LiveAvailabilityService liveAvailabilityService;

	private DataHostLms firstHostLms;
	private DataHostLms secondHostLms;
	private SierraItemsAPIFixture sierraItemsAPIFixture;

	@BeforeAll
	@SneakyThrows
	void beforeAll(MockServerClient mockServerClient) {
		final String FIRST_HOST_LMS_BASE_URL = "https://first-live-availability-system.com";
		final String FIRST_HOST_LMS_CODE = "first-local-system";
		final String FIRST_HOST_LMS_TOKEN = "first-system-token";
		final String FIRST_HOST_LMS_KEY = "first-system-key";
		final String FIRST_HOST_LMS_SECRET = "first-system-secret";

		final String SECOND_HOST_LMS_BASE_URL = "https://second-live-availability-system.com";
		final String SECOND_HOST_LMS_CODE = "second-local-system";
		final String SECOND_SYSTEM_TOKEN = "second-system-token";
		final String SECOND_SYSTEM_KEY = "second-system-key";
		final String SECOND_SYSTEM_SECRET = "second-system-secret";

		SierraTestUtils.mockFor(mockServerClient, FIRST_HOST_LMS_BASE_URL)
			.setValidCredentials(FIRST_HOST_LMS_KEY, FIRST_HOST_LMS_SECRET, FIRST_HOST_LMS_TOKEN, 60);

		SierraTestUtils.mockFor(mockServerClient, SECOND_HOST_LMS_BASE_URL)
			.setValidCredentials(SECOND_SYSTEM_KEY, SECOND_SYSTEM_SECRET, SECOND_SYSTEM_TOKEN, 60);

		hostLmsFixture.deleteAll();

		firstHostLms = hostLmsFixture.createSierraHostLms(FIRST_HOST_LMS_CODE,
			FIRST_HOST_LMS_KEY, FIRST_HOST_LMS_SECRET, FIRST_HOST_LMS_BASE_URL, "item");

		secondHostLms = hostLmsFixture.createSierraHostLms(SECOND_HOST_LMS_CODE,
			SECOND_SYSTEM_KEY, SECOND_SYSTEM_SECRET, SECOND_HOST_LMS_BASE_URL, "item");

		sierraItemsAPIFixture = sierraApiFixtureProvider.items(mockServerClient, null);
	}

	@BeforeEach
	void beforeEach() {
		clusterRecordFixture.deleteAll();
		referenceValueMappingFixture.deleteAll();
		agencyFixture.deleteAll();
	}

	@Test
	void shouldGetItemsFromMultipleHostLms() {
		// Arrange
		final var clusterRecord = clusterRecordFixture.createClusterRecord(randomUUID(), randomUUID());

		final var firstHostLmsBibId = "465675";

		bibRecordFixture.createBibRecord(randomUUID(), firstHostLms.getId(),
			firstHostLmsBibId, clusterRecord);

		final var secondHostLmsBibId = "767648";

		bibRecordFixture.createBibRecord(randomUUID(), secondHostLms.getId(),
			secondHostLmsBibId, clusterRecord);

		final var firstAgency = agencyFixture.defineAgency("first-agency",
			"First Agency", firstHostLms);

		mapLocationToAgency("ab6", firstHostLms, firstAgency);

		final var firstItemFromFirstHostLmsBarcode = "6565750674";
		final var secondItemFromFirstHostLmsBarcode = "30800005238487";

		sierraItemsAPIFixture.itemsForBibId(firstHostLmsBibId, List.of(
			SierraItem.builder()
				.id("1000002")
				.barcode(firstItemFromFirstHostLmsBarcode)
				.callNumber("BL221 .C48")
				.statusCode("-")
				.itemType("999")
				.locationCode("ab6")
				.locationName("King 6th Floor")
				.build(),
			SierraItem.builder()
				.id("1000001")
				.barcode(secondItemFromFirstHostLmsBarcode)
				.callNumber("HD9787.U5 M43 1969")
				.statusCode("-")
				.dueDate(Instant.parse("2021-02-25T12:00:00Z"))
				.itemType("999")
				.locationCode("ab6")
				.locationName("King 6th Floor")
				.build()
		), 150);

		final var secondAgency = agencyFixture.defineAgency("second-agency",
			"Second Agency", secondHostLms);

		mapLocationToAgency("ab5", secondHostLms, secondAgency);
		mapLocationToAgency("ab7", secondHostLms, secondAgency);

		final var firstItemFromSecondHostLmsBarcode = "9849123490";
		final var secondItemFromSecondHostLmsBarcode = "30800005315459";
		final var thirdItemFromSecondHostLmsBarcode = "30800005208449";

		sierraItemsAPIFixture.itemsForBibId(secondHostLmsBibId, List.of(
			SierraItem.builder()
				.id("8757567")
				.barcode(firstItemFromSecondHostLmsBarcode)
				.callNumber("BL221 .C48")
				.statusCode("-")
				.itemType("999")
				.locationCode("ab5")
				.locationName("King 5th Floor")
				.build(),
			SierraItem.builder()
				.id("8275735")
				.barcode(secondItemFromSecondHostLmsBarcode)
				.callNumber("HX157 .H8")
				.statusCode("-")
				.itemType("999")
				.locationCode("ab7")
				.locationName("King 7th Floor")
				.build(),
			SierraItem.builder()
				.id("72465635")
				.barcode(thirdItemFromSecondHostLmsBarcode)
				.callNumber("HC336.2 .S74 1969")
				.statusCode("-")
				.itemType("999")
				.locationCode("ab7")
				.locationName("King 7th Floor")
				.build()
		), 50);

		referenceValueMappingFixture.defineLocalToCanonicalItemTypeRangeMapping(
			firstHostLms.getCode(), 999, 999, "loanable-item");

		referenceValueMappingFixture.defineLocalToCanonicalItemTypeRangeMapping(
			secondHostLms.getCode(), 999, 999, "loanable-item");

		// Act
		final var report = checkAvailability(clusterRecord);

		// Assert

		// This is a compromise that checks the rough identity of each item
		// without duplicating checks for many fields
		// The order is important due to the sorting applied to items
		assertThat(report, allOf(
			hasItems(5),
			hasItemsInOrder(
				allOf(
					hasBarcode(firstItemFromSecondHostLmsBarcode),
					hasSourceHostLmsCode(secondHostLms)
				),
				allOf(
					hasBarcode(firstItemFromFirstHostLmsBarcode),
					hasSourceHostLmsCode(firstHostLms)
				),
				allOf(
					hasBarcode(secondItemFromFirstHostLmsBarcode),
					hasSourceHostLmsCode(firstHostLms)
				),
				allOf(
					hasBarcode(thirdItemFromSecondHostLmsBarcode),
					hasSourceHostLmsCode(secondHostLms)
				),
				allOf(
					hasBarcode(secondItemFromSecondHostLmsBarcode),
					hasSourceHostLmsCode(secondHostLms)
				)
			),
			hasNoErrors()
		));

		assertAllAvailableItemsHaveSameAvailabilityDate(
			getValue(report, AvailabilityReport::getItems, emptyList()));
	}

	@Test
	void shouldExcludeItemsThatAreNotAssociatedWithAnAgency() {
		// Arrange
		final var clusterRecord = clusterRecordFixture.createClusterRecord(randomUUID(), randomUUID());

		final var bibId = "658366";

		bibRecordFixture.createBibRecord(randomUUID(), firstHostLms.getId(), bibId, clusterRecord);

		sierraItemsAPIFixture.itemsForBibId(bibId, List.of(
			SierraItem.builder()
				.id("3752566")
				.barcode("5362553")
				.callNumber("BL221 .C48")
				.statusCode("-")
				.itemType("1")
				.locationCode("unmapped")
				.locationName("Unmapped")
				.build()
		));

		referenceValueMappingFixture.defineLocalToCanonicalItemTypeRangeMapping(
			firstHostLms.getCode(), 1, 1, "loanable-item");

		// Act
		final var report = checkAvailability(clusterRecord);

		// Assert
		assertThat(report, allOf(
			hasNoItems(),
			hasNoErrors()
		));
	}

	@Test
	void shouldExcludeItemsThatAreAssociatedWithAnAgencyWithoutAHostLms() {
		// Arrange
		final var clusterRecord = clusterRecordFixture.createClusterRecord(randomUUID(), randomUUID());

		final var bibId = "274656";

		bibRecordFixture.createBibRecord(randomUUID(), firstHostLms.getId(), bibId, clusterRecord);

		final var knownLocationCode = "known-location";

		final var agency = agencyFixture.defineAgencyWithNoHostLms("agency", "Agency");

		mapLocationToAgency(knownLocationCode, firstHostLms, agency);

		sierraItemsAPIFixture.itemsForBibId(bibId, List.of(
			SierraItem.builder()
				.id("3752566")
				.barcode("5362553")
				.callNumber("BL221 .C48")
				.statusCode("-")
				.itemType("1")
				.locationCode(knownLocationCode)
				.locationName("Known Location")
				.build()
		));

		referenceValueMappingFixture.defineLocalToCanonicalItemTypeRangeMapping(
			firstHostLms.getCode(), 1, 1, "loanable-item");

		// Act
		final var report = checkAvailability(clusterRecord);

		// Assert
		assertThat(report, allOf(
			hasNoItems(),
			hasNoErrors()
		));
	}

	@Test
	void shouldExcludeItemsThatAreAssociatedWithNonSupplyingAgency() {
		// Arrange
		final var clusterRecord = clusterRecordFixture.createClusterRecord(randomUUID(), randomUUID());

		final var bibId = "573652";

		bibRecordFixture.createBibRecord(randomUUID(), firstHostLms.getId(), bibId, clusterRecord);

		final var knownLocationCode = "known-location";

		final var agency = agencyFixture.defineAgency(DataAgency.builder()
			.id(randomUUID())
			.code("agency")
			.name("Agency")
			.isSupplyingAgency(false)
			.hostLms(firstHostLms)
			.build());

		mapLocationToAgency(knownLocationCode, firstHostLms, agency);

		sierraItemsAPIFixture.itemsForBibId(bibId, List.of(
			SierraItem.builder()
				.id("3752566")
				.barcode("5362553")
				.callNumber("BL221 .C48")
				.statusCode("-")
				.itemType("1")
				.locationCode(knownLocationCode)
				.locationName("Known Location")
				.build()
		));

		referenceValueMappingFixture.defineLocalToCanonicalItemTypeRangeMapping(
			firstHostLms.getCode(), 1, 1, "loanable-item");

		// Act
		final var report = checkAvailability(clusterRecord);

		// Assert
		assertThat(report, allOf(
			hasNoItems(),
			hasNoErrors()
		));
	}

	@Test
	void shouldExcludeItemsThatAreAssociatedWithAgencyWithNoParticipationInformation() {
		// Arrange
		final var clusterRecord = clusterRecordFixture.createClusterRecord(randomUUID(), randomUUID());

		final var bibId = "364568";

		bibRecordFixture.createBibRecord(randomUUID(), firstHostLms.getId(), bibId, clusterRecord);

		final var knownLocationCode = "known-location";

		final var agency = agencyFixture.defineAgency(DataAgency.builder()
			.id(randomUUID())
			.code("agency")
			.name("Agency")
			// Unknown whether they are supplying or not
			.isSupplyingAgency(null)
			.hostLms(firstHostLms)
			.build());

		mapLocationToAgency(knownLocationCode, firstHostLms, agency);

		sierraItemsAPIFixture.itemsForBibId(bibId, List.of(
			SierraItem.builder()
				.id("3752566")
				.barcode("5362553")
				.callNumber("BL221 .C48")
				.statusCode("-")
				.itemType("1")
				.locationCode(knownLocationCode)
				.locationName("Known Location")
				.build()
		));

		referenceValueMappingFixture.defineLocalToCanonicalItemTypeRangeMapping(
			firstHostLms.getCode(), 1, 1, "loanable-item");

		// Act
		final var report = checkAvailability(clusterRecord);

		// Assert
		assertThat(report, allOf(
			hasNoItems(),
			hasNoErrors()
		));
	}

	@Test
	void shouldReportZeroItemsWhenHostLmsRespondsWithZeroItems() {
		// Arrange
		final var clusterRecord = clusterRecordFixture.createClusterRecord(randomUUID(), randomUUID());

		bibRecordFixture.createBibRecord(randomUUID(), firstHostLms.getId(),
			"762354", clusterRecord);

		sierraItemsAPIFixture.zeroItemsResponseForBibId("762354");

		// Act
		final var report = checkAvailability(clusterRecord);

		// Assert
		assertThat(report, allOf(
			hasNoItems(),
			hasNoErrors()
		));
	}

	@Test
	void shouldReportFailuresFetchingItemsFromHostLms() {
		// Arrange
		final var clusterRecord = clusterRecordFixture.createClusterRecord(randomUUID(), randomUUID());

		bibRecordFixture.createBibRecord(randomUUID(), firstHostLms.getId(),
			"839552", clusterRecord);

		sierraItemsAPIFixture.errorResponseForBibId("839552");

		// Act
		final var report = checkAvailability(clusterRecord);

		// Assert
		assertThat(report, allOf(
			hasNoItems(),
			hasError("Failed to fetch items for bib: 839552 from host: first-local-system")
		));
	}

	@Test
	void shouldFailWhenCannotFindClusterRecordById() {
		// Arrange
		final var clusterRecordId = randomUUID();

		// Act
		final var exception = assertThrows(CannotFindClusterRecordException.class,
			() -> checkAvailability(clusterRecordId));

		// Assert
		assertThat(exception, hasMessage("Cannot find cluster record for: " + clusterRecordId));
	}

	@Test
	void shouldReportZeroItemsWhenClusterRecordHasNoContributingBibs() {
		// Arrange
		final var clusterRecord = clusterRecordFixture.createClusterRecord(randomUUID(), randomUUID());

		// Act
		final var report = checkAvailability(clusterRecord);

		// Assert
		assertThat(report, allOf(
			hasNoItems(),
			hasNoErrors()
		));
	}

	@Test
	void shouldFailWhenHostLmsCannotBeFoundForBib() {
		final var clusterRecordId = randomUUID();
		final var bibRecordId = randomUUID();
		final var unknownHostId = randomUUID();

		final var clusterRecord = clusterRecordFixture.createClusterRecord(clusterRecordId, clusterRecordId);

		bibRecordFixture.createBibRecord(bibRecordId, unknownHostId,
			"7657673", clusterRecord);

		final var exception = assertThrows(UnknownHostLmsException.class,
			() -> checkAvailability(clusterRecord));

		assertThat(exception, hasMessage("No Host LMS found for ID: " + unknownHostId));
	}

	private AvailabilityReport checkAvailability(UUID clusterRecordId) {
		return singleValueFrom(liveAvailabilityService.checkAvailability(clusterRecordId,
			Optional.empty()));
	}

	private AvailabilityReport checkAvailability(ClusterRecord clusterRecord) {
		return checkAvailability(clusterRecord.getId());
	}

	private void mapLocationToAgency(String locationCode,
		DataHostLms hostLms, DataAgency agency) {

		referenceValueMappingFixture.defineLocationToAgencyMapping(
			hostLms.getCode(), locationCode, agency.getCode());
	}

	private static void assertAllAvailableItemsHaveSameAvailabilityDate(List<Item> items) {
		final var groupedByAvailabilityDate = items.stream()
			.filter(Item::isAvailable)
			.collect(groupingBy(Item::getAvailableDate));

		assertThat("Only one availability date", groupedByAvailabilityDate.keySet(), hasSize(1));

		assertThat("Availability date is present",
			groupedByAvailabilityDate.keySet().stream().findFirst().isPresent(), is(true));
		assertThat("Availability date is not null",
			groupedByAvailabilityDate.keySet().stream().findFirst().get(), is(notNullValue()));
	}
}
