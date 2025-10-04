package org.olf.dcb.api;

import static io.micronaut.http.HttpStatus.BAD_REQUEST;
import static io.micronaut.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.olf.dcb.test.matchers.AvailabilityResponseMatchers.hasAgency;
import static org.olf.dcb.test.matchers.AvailabilityResponseMatchers.hasAvailabilityDate;
import static org.olf.dcb.test.matchers.AvailabilityResponseMatchers.hasBarcode;
import static org.olf.dcb.test.matchers.AvailabilityResponseMatchers.hasCallNumber;
import static org.olf.dcb.test.matchers.AvailabilityResponseMatchers.hasCanonicalItemType;
import static org.olf.dcb.test.matchers.AvailabilityResponseMatchers.hasClusterRecordId;
import static org.olf.dcb.test.matchers.AvailabilityResponseMatchers.hasDueDate;
import static org.olf.dcb.test.matchers.AvailabilityResponseMatchers.hasHostLms;
import static org.olf.dcb.test.matchers.AvailabilityResponseMatchers.hasId;
import static org.olf.dcb.test.matchers.AvailabilityResponseMatchers.hasItems;
import static org.olf.dcb.test.matchers.AvailabilityResponseMatchers.hasLocalItemType;
import static org.olf.dcb.test.matchers.AvailabilityResponseMatchers.hasLocation;
import static org.olf.dcb.test.matchers.AvailabilityResponseMatchers.hasNoDueDate;
import static org.olf.dcb.test.matchers.AvailabilityResponseMatchers.hasNoErrors;
import static org.olf.dcb.test.matchers.AvailabilityResponseMatchers.hasNoHolds;
import static org.olf.dcb.test.matchers.AvailabilityResponseMatchers.hasNoItems;
import static org.olf.dcb.test.matchers.AvailabilityResponseMatchers.hasSourceHostLms;
import static org.olf.dcb.test.matchers.AvailabilityResponseMatchers.hasStatus;
import static org.olf.dcb.test.matchers.AvailabilityResponseMatchers.isNotRequestable;
import static org.olf.dcb.test.matchers.AvailabilityResponseMatchers.isRequestable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockserver.client.MockServerClient;
import org.olf.dcb.core.interaction.sierra.SierraApiFixtureProvider;
import org.olf.dcb.core.interaction.sierra.SierraItem;
import org.olf.dcb.core.interaction.sierra.SierraItemsAPIFixture;
import org.olf.dcb.test.AgencyFixture;
import org.olf.dcb.test.BibRecordFixture;
import org.olf.dcb.test.ClusterRecordFixture;
import org.olf.dcb.test.HostLmsFixture;
import org.olf.dcb.test.ReferenceValueMappingFixture;

import io.micronaut.http.client.exceptions.HttpClientResponseException;
import jakarta.inject.Inject;
import lombok.SneakyThrows;
import services.k_int.interaction.sierra.SierraTestUtils;
import services.k_int.test.mockserver.MockServerMicronautTest;

@MockServerMicronautTest
@TestInstance(PER_CLASS)
class LiveAvailabilityApiTests {
	private static final String CATALOGUING_HOST_LMS_CODE = "live-availability-cataloguing";
	private static final String CIRCULATING_HOST_LMS_CODE = "live-availability-circulating";

	private static final String SUPPLYING_AGENCY_CODE = "345test";
	private static final String SUPPLYING_AGENCY_NAME = "Test College";

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
	private LiveAvailabilityApiClient liveAvailabilityApiClient;

	private SierraItemsAPIFixture sierraItemsAPIFixture;

	@BeforeAll
	@SneakyThrows
	public void beforeAll(MockServerClient mockServerClient) {
		final String TOKEN = "test-token";
		final String BASE_URL = "https://live-availability-api-tests.com";
		final String KEY = "live-availability-key";
		final String SECRET = "live-availability-secret";

		SierraTestUtils.mockFor(mockServerClient, BASE_URL)
			.setValidCredentials(KEY, SECRET, TOKEN, 60);

		sierraItemsAPIFixture = sierraApiFixtureProvider.itemsApiFor(mockServerClient);

		hostLmsFixture.deleteAll();

		hostLmsFixture.createSierraHostLms(CATALOGUING_HOST_LMS_CODE, KEY, SECRET, BASE_URL, "item");
		hostLmsFixture.createSierraHostLms(CIRCULATING_HOST_LMS_CODE, KEY, SECRET, BASE_URL, "item");
	}

	@BeforeEach
	void beforeEach() {
		clusterRecordFixture.deleteAll();
		agencyFixture.deleteAll();
		referenceValueMappingFixture.deleteAll();
	}

	@Test
	void canProvideAListOfAvailableItems() {
		// Arrange
		final var clusterRecordId = randomUUID();
		final var sourceRecordId = "798472";

		defineClusterRecordWithSingleBib(clusterRecordId, sourceRecordId);

		final var locationCode = "ab6";

		sierraItemsAPIFixture.itemsForBibId("798472", List.of(
			SierraItem.builder()
				.id("1000002")
				.barcode("6565750674")
				.callNumber("BL221 .C48")
				.statusCode("-")
				.itemType("999")
				.locationCode(locationCode)
				.locationName("King 6th Floor")
				.suppressed(false)
				.deleted(false)
				.build(),
			SierraItem.builder()
				.id("1000001")
				.barcode("30800005238487")
				.callNumber("HD9787.U5 M43 1969")
				.statusCode("-")
				.dueDate(Instant.parse("2021-02-25T12:00:00Z"))
				.itemType("999")
				.locationCode(locationCode)
				.locationName("King 6th Floor")
				.build()
		));

		referenceValueMappingFixture.defineLocationToAgencyMapping(
			CATALOGUING_HOST_LMS_CODE, locationCode, SUPPLYING_AGENCY_CODE);

		agencyFixture.defineAgency(SUPPLYING_AGENCY_CODE, SUPPLYING_AGENCY_NAME,
			hostLmsFixture.findByCode(CIRCULATING_HOST_LMS_CODE));

		referenceValueMappingFixture.defineLocalToCanonicalItemTypeRangeMapping(
			CIRCULATING_HOST_LMS_CODE, 999, 999, "BKM");

		// Act
		final var report = liveAvailabilityApiClient.getAvailabilityReport(clusterRecordId);

		// Assert
		assertThat(report, allOf(
			notNullValue(),
			hasClusterRecordId(clusterRecordId),
			hasNoErrors(),
			hasItems(
				allOf(
					notNullValue(),
					hasId("1000002"),
					hasBarcode("6565750674"),
					hasCallNumber("BL221 .C48"),
					hasNoDueDate(),
					isRequestable(),
					hasStatus("AVAILABLE"),
					hasNoHolds(),
					hasAvailabilityDate(),
					hasLocalItemType("999"),
					hasCanonicalItemType("BKM"),
					hasLocation(locationCode, "King 6th Floor"),
					hasAgency(SUPPLYING_AGENCY_CODE, SUPPLYING_AGENCY_NAME),
					hasHostLms(CIRCULATING_HOST_LMS_CODE),
					hasSourceHostLms(CATALOGUING_HOST_LMS_CODE)
				),
				allOf(
					notNullValue(),
					hasId("1000001"),
					hasBarcode("30800005238487"),
					hasCallNumber("HD9787.U5 M43 1969"),
					hasDueDate("2021-02-25T12:00:00Z"),
					isNotRequestable(),
					hasStatus("CHECKED_OUT"),
					hasNoHolds(),
					hasAvailabilityDate(),
					hasLocalItemType("999"),
					hasCanonicalItemType("BKM"),
					hasLocation(locationCode, "King 6th Floor"),
					hasAgency(SUPPLYING_AGENCY_CODE, SUPPLYING_AGENCY_NAME),
					hasHostLms(CIRCULATING_HOST_LMS_CODE),
					hasSourceHostLms(CATALOGUING_HOST_LMS_CODE)
				)
			)
		));
	}

	@Test
	void shouldExcludeSuppressedItems() {
		// Arrange
		final var clusterRecordId = randomUUID();
		final var sourceRecordId = "364255";

		defineClusterRecordWithSingleBib(clusterRecordId, sourceRecordId);

		final var locationCode = "example-location";

		sierraItemsAPIFixture.itemsForBibId(sourceRecordId, List.of(
			SierraItem.builder()
				.id("3642679")
				.locationCode(locationCode)
				.locationName("Example Location")
				.suppressed(true)
				.deleted(false)
				.itemType("999")
				.build()));

		referenceValueMappingFixture.defineLocationToAgencyMapping(
			CATALOGUING_HOST_LMS_CODE, locationCode, SUPPLYING_AGENCY_CODE);

		agencyFixture.defineAgency(SUPPLYING_AGENCY_CODE, SUPPLYING_AGENCY_NAME,
			hostLmsFixture.findByCode(CIRCULATING_HOST_LMS_CODE));

		referenceValueMappingFixture.defineLocalToCanonicalItemTypeRangeMapping(
			CATALOGUING_HOST_LMS_CODE, 999, 999, "loanable-item");

		// Act
		final var report = liveAvailabilityApiClient.getAvailabilityReport(clusterRecordId);

		// Assert
		assertThat(report, allOf(
			notNullValue(),
			hasClusterRecordId(clusterRecordId),
			hasNoItems(),
			hasNoErrors()
		));
	}

	@Test
	void shouldExcludeDeletedItems() {
		// Arrange
		final var clusterRecordId = randomUUID();
		final var sourceRecordId = "728951";

		defineClusterRecordWithSingleBib(clusterRecordId, sourceRecordId);

		final var locationCode = "example-location";

		sierraItemsAPIFixture.itemsForBibId(sourceRecordId, List.of(
			SierraItem.builder()
				.id("98725178")
				.locationCode(locationCode)
				.locationName("Example Location")
				.suppressed(false)
				.deleted(true)
				.itemType("999")
				.build()));

		referenceValueMappingFixture.defineLocationToAgencyMapping(
			CATALOGUING_HOST_LMS_CODE, locationCode, SUPPLYING_AGENCY_CODE);

		agencyFixture.defineAgency(SUPPLYING_AGENCY_CODE, SUPPLYING_AGENCY_NAME,
			hostLmsFixture.findByCode(CIRCULATING_HOST_LMS_CODE));

		referenceValueMappingFixture.defineLocalToCanonicalItemTypeRangeMapping(
			CATALOGUING_HOST_LMS_CODE, 999, 999, "loanable-item");

		// Act
		final var report = liveAvailabilityApiClient.getAvailabilityReport(clusterRecordId);

		// Assert
		assertThat(report, allOf(
			notNullValue(),
			hasClusterRecordId(clusterRecordId),
			hasNoItems(),
			hasNoErrors()
		));
	}

	@Test
	void shouldFailWhenClusterRecordCannotBeFound() {
		// Arrange
		final var clusterRecordId = randomUUID();

		// Act
		final var exception = assertThrows(HttpClientResponseException.class,
			() -> liveAvailabilityApiClient.getAvailabilityReport(clusterRecordId));

		// Assert
		final var response = exception.getResponse();

		assertThat("Should return a bad request status", response.getStatus(), is(BAD_REQUEST));

		final var optionalBody = response.getBody(String.class);

		assertThat("Response should have body", optionalBody.isPresent(), is(true));
		assertThat("Body should contain error", optionalBody.get(),
			is("Cannot find cluster record for: " + clusterRecordId));
	}

	@Test
	void shouldFailWhenHostLmsCannotBeFoundForBib() {
		// Arrange
		final var clusterRecordId = randomUUID();
		final var bibRecordId = randomUUID();
		final var unknownHostId = randomUUID();

		final var clusterRecord = clusterRecordFixture.createClusterRecord(clusterRecordId, clusterRecordId);

		bibRecordFixture.createBibRecord(bibRecordId, unknownHostId,
			"7657673", clusterRecord);

		// Act
		final var exception = assertThrows(HttpClientResponseException.class,
			() -> liveAvailabilityApiClient.getAvailabilityReport(clusterRecordId));

		// Assert
		final var response = exception.getResponse();

		assertThat("Should return a server error status", response.getStatus(), is(INTERNAL_SERVER_ERROR));

		final var optionalBody = response.getBody(String.class);

		assertThat("Response should have body", optionalBody.isPresent(), is(true));
		assertThat("Body should contain error", optionalBody.get(),
			is("No Host LMS found for ID: " + unknownHostId));
	}

	@Test
	void shouldProvideEmptyReportWhenClusterRecordHasNoContributingBibs() {
		// Arrange
		final var clusterRecordId = randomUUID();

		clusterRecordFixture.createClusterRecord(clusterRecordId, clusterRecordId);

		// Act
		final var report = liveAvailabilityApiClient.getAvailabilityReport(clusterRecordId);

		// Assert
		assertThat(report, allOf(
			notNullValue(),
			hasClusterRecordId(clusterRecordId),
			hasNoItems(),
			hasNoErrors()
		));
	}

	private void defineClusterRecordWithSingleBib(UUID clusterRecordId, String sourceRecordId) {
		final var clusterRecord = clusterRecordFixture.createClusterRecord(
			clusterRecordId, clusterRecordId);

		final var hostLms = hostLmsFixture.findByCode(CATALOGUING_HOST_LMS_CODE);

		final var sourceSystemId = hostLms.getId();

		bibRecordFixture.createBibRecord(randomUUID(), sourceSystemId,
			sourceRecordId, clusterRecord);
	}
}
