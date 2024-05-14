package org.olf.dcb.item.availability;

import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;
import static org.olf.dcb.test.matchers.ItemMatchers.hasBarcode;
import static org.olf.dcb.test.matchers.ThrowableMatchers.hasMessage;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.hamcrest.Matcher;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockserver.client.MockServerClient;
import org.olf.dcb.core.UnknownHostLmsException;
import org.olf.dcb.core.interaction.sierra.SierraApiFixtureProvider;
import org.olf.dcb.core.interaction.sierra.SierraItem;
import org.olf.dcb.core.interaction.sierra.SierraItemsAPIFixture;
import org.olf.dcb.core.model.DataAgency;
import org.olf.dcb.core.model.DataHostLms;
import org.olf.dcb.core.model.Item;
import org.olf.dcb.core.model.clustering.ClusterRecord;
import org.olf.dcb.request.resolution.CannotFindClusterRecordException;
import org.olf.dcb.request.resolution.NoBibsForClusterRecordException;
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

		sierraItemsAPIFixture = sierraApiFixtureProvider.itemsApiFor(mockServerClient);
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

		bibRecordFixture.createBibRecord(randomUUID(), firstHostLms.getId(),
			"465675", clusterRecord);

		bibRecordFixture.createBibRecord(randomUUID(), secondHostLms.getId(),
			"767648", clusterRecord);

		final var firstAgency = agencyFixture.defineAgency("first-agency",
			"First Agency", firstHostLms);

		mapLocationToAgency("ab6", firstHostLms, firstAgency);

		sierraItemsAPIFixture.itemsForBibId("465675", List.of(
			SierraItem.builder()
				.id("1000002")
				.barcode("6565750674")
				.callNumber("BL221 .C48")
				.statusCode("-")
				.itemType("999")
				.locationCode("ab6")
				.locationName("King 6th Floor")
				.build(),
			SierraItem.builder()
				.id("1000001")
				.barcode("30800005238487")
				.callNumber("HD9787.U5 M43 1969")
				.statusCode("-")
				.dueDate(Instant.parse("2021-02-25T12:00:00Z"))
				.itemType("999")
				.locationCode("ab6")
				.locationName("King 6th Floor")
				.build()
		));

		final var secondAgency = agencyFixture.defineAgency("second-agency",
			"Second Agency", secondHostLms);

		mapLocationToAgency("ab5", secondHostLms, secondAgency);
		mapLocationToAgency("ab7", secondHostLms, secondAgency);

		sierraItemsAPIFixture.itemsForBibId("767648", List.of(
			SierraItem.builder()
				.id("8757567")
				.barcode("9849123490")
				.callNumber("BL221 .C48")
				.statusCode("-")
				.itemType("999")
				.locationCode("ab5")
				.locationName("King 5th Floor")
				.build(),
			SierraItem.builder()
				.id("8275735")
				.barcode("30800005315459")
				.callNumber("HX157 .H8")
				.statusCode("-")
				.itemType("999")
				.locationCode("ab7")
				.locationName("King 7th Floor")
				.build(),
			SierraItem.builder()
				.id("72465635")
				.barcode("30800005208449")
				.callNumber("HC336.2 .S74 1969")
				.statusCode("-")
				.itemType("999")
				.locationCode("ab7")
				.locationName("King 7th Floor")
				.build()
		));

		// Act
		final var report = checkAvailability(clusterRecord);

		// Assert
		// This is a compromise that checks the rough identity of each item
		// without duplicating checks for many fields
		// The order is important due to the sorting applied to items
		assertThat(report, allOf(
			hasItems(5),
			hasItemsInOrder(
				hasBarcode("9849123490"),
				hasBarcode("6565750674"),
				hasBarcode("30800005238487"),
				hasBarcode("30800005208449"),
				hasBarcode("30800005315459")
			),
			hasNoErrors()
		));
	}

	@Test
	void shouldExcludeItemsThatAreNotAssociatedWithAnAgency() {
		// Arrange
		final var clusterRecord = clusterRecordFixture.createClusterRecord(randomUUID(), randomUUID());

		bibRecordFixture.createBibRecord(randomUUID(), firstHostLms.getId(),
			"362563", clusterRecord);

		sierraItemsAPIFixture.itemsForBibId("362563", List.of(
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
	void shouldFailWhenClusterRecordHasNoContributingBibs() {
		// Arrange
		final var clusterRecord = clusterRecordFixture.createClusterRecord(randomUUID(), randomUUID());

		// Act
		final var exception = assertThrows(NoBibsForClusterRecordException.class,
			() -> checkAvailability(clusterRecord));

		// Assert
		assertThat(exception, hasMessage(
			"Cluster record: \"" + clusterRecord.getId() + "\" has no bibs"));
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
		return singleValueFrom(liveAvailabilityService.checkAvailability(clusterRecordId));
	}

	private AvailabilityReport checkAvailability(ClusterRecord clusterRecord) {
		return checkAvailability(clusterRecord.getId());
	}

	private void mapLocationToAgency(String locationCode,
		DataHostLms hostLms, DataAgency agency) {

		referenceValueMappingFixture.defineLocationToAgencyMapping(
			hostLms.getCode(), locationCode, agency.getCode());
	}

	private static Matcher<AvailabilityReport> hasNoItems() {
		return hasProperty("items", empty());
	}

	private static Matcher<AvailabilityReport> hasItems(int expectedCount) {
		return hasProperty("items", hasSize(expectedCount));
	}

	@SafeVarargs
	private static Matcher<AvailabilityReport> hasItemsInOrder(Matcher<Item>... matchers) {
		return hasProperty("items", contains(matchers));
	}

	private static Matcher<AvailabilityReport> hasNoErrors() {
		return hasProperty("errors", empty());
	}

	private static Matcher<AvailabilityReport> hasError(String expectedMessage) {
		return hasProperty("errors", containsInAnyOrder(
			hasProperty("message", is(expectedMessage)))
		);
	}
}
