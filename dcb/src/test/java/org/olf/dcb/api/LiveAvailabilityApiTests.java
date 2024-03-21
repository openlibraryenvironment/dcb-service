package org.olf.dcb.api;

import static io.micronaut.http.HttpStatus.BAD_REQUEST;
import static io.micronaut.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

import java.util.List;

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

		sierraItemsAPIFixture.twoItemsResponseForBibId("798472");

		hostLmsFixture.deleteAll();

		hostLmsFixture.createSierraHostLms(CATALOGUING_HOST_LMS_CODE, KEY, SECRET, BASE_URL, "item");
		hostLmsFixture.createSierraHostLms(CIRCULATING_HOST_LMS_CODE, KEY, SECRET, BASE_URL, "item");
	}

	@BeforeEach
	void beforeEach() {
		clusterRecordFixture.deleteAll();
	}

	@Test
	void canProvideAListOfAvailableItems() {
		// Arrange
		final var clusterRecordId = randomUUID();

		final var clusterRecord = clusterRecordFixture.createClusterRecord(clusterRecordId, clusterRecordId);

		final var hostLms = hostLmsFixture.findByCode(CATALOGUING_HOST_LMS_CODE);

		final var sourceSystemId = hostLms.getId();

		bibRecordFixture.createBibRecord(randomUUID(), sourceSystemId,
			"798472", clusterRecord);

		final var locationCode = "ab6";

		referenceValueMappingFixture.defineLocationToAgencyMapping(
			CATALOGUING_HOST_LMS_CODE, locationCode, SUPPLYING_AGENCY_CODE);

		agencyFixture.defineAgency(SUPPLYING_AGENCY_CODE, SUPPLYING_AGENCY_NAME,
			hostLmsFixture.findByCode(CIRCULATING_HOST_LMS_CODE));

		// Act
		final var report = liveAvailabilityApiClient.getAvailabilityReport(clusterRecordId);

		// Assert
		assertThat(report, is(notNullValue()));

		final var items = report.getItemList();

		assertThat(items, is(notNullValue()));
		assertThat(items.size(), is(2));
		assertThat(report.getClusteredBibId(), is(clusterRecordId));

		final var firstItem = items.get(0);

		assertThat(firstItem, is(notNullValue()));
		assertThat(firstItem.getId(), is("1000002"));
		assertThat(firstItem.getBarcode(), is("6565750674"));
		assertThat(firstItem.getCallNumber(), is("BL221 .C48"));
		assertThat(firstItem.getDueDate(), is(nullValue()));
		assertThat(firstItem.getIsRequestable(), is(true));
		assertThat(firstItem.getHoldCount(), is(0));
		assertThat(firstItem.getLocalItemType(), is("999"));
		assertThat(firstItem.getCanonicalItemType(), is("BKM"));
		assertThat(firstItem.getAgency().getCode(), is(SUPPLYING_AGENCY_CODE));
		assertThat(firstItem.getAgency().getDescription(), is(SUPPLYING_AGENCY_NAME));

		final var firstItemStatus = firstItem.getStatus();

		assertThat(firstItemStatus, is(notNullValue()));
		assertThat(firstItemStatus.getCode(), is("AVAILABLE"));

		final var firstItemLocation = firstItem.getLocation();

		assertThat(firstItemLocation, is(notNullValue()));
		assertThat(firstItemLocation.getCode(), is(locationCode));
		assertThat(firstItemLocation.getName(), is("King 6th Floor"));

		final var secondItem = items.get(1);

		assertThat(secondItem, is(notNullValue()));
		assertThat(secondItem.getId(), is("1000001"));
		assertThat(secondItem.getBarcode(), is("30800005238487"));
		assertThat(secondItem.getCallNumber(), is("HD9787.U5 M43 1969"));
		assertThat(secondItem.getDueDate(), is("2021-02-25T12:00:00Z"));
		assertThat(secondItem.getIsRequestable(), is(false));
		assertThat(secondItem.getHoldCount(), is(0));
		assertThat(secondItem.getLocalItemType(), is("999"));
		assertThat(secondItem.getCanonicalItemType(), is("BKM"));
		assertThat(secondItem.getAgency().getCode(), is(SUPPLYING_AGENCY_CODE));
		assertThat(secondItem.getAgency().getDescription(), is(SUPPLYING_AGENCY_NAME));

		final var secondItemStatus = secondItem.getStatus();

		assertThat(secondItemStatus, is(notNullValue()));
		assertThat(secondItemStatus.getCode(), is("CHECKED_OUT"));

		final var secondItemLocation = secondItem.getLocation();

		assertThat(secondItemLocation, is(notNullValue()));
		assertThat(secondItemLocation.getCode(), is(locationCode));
		assertThat(secondItemLocation.getName(), is("King 6th Floor"));
	}

	@Test
	void shouldTolerateItemsFromAnUnknownAgency() {
		// Arrange
		final var clusterRecordId = randomUUID();

		final var clusterRecord = clusterRecordFixture.createClusterRecord(clusterRecordId, clusterRecordId);

		final var hostLms = hostLmsFixture.findByCode(CATALOGUING_HOST_LMS_CODE);

		final var sourceSystemId = hostLms.getId();
		final var sourceRecordId = "267635";

		bibRecordFixture.createBibRecord(randomUUID(), sourceSystemId, sourceRecordId, clusterRecord);

		sierraItemsAPIFixture.itemsForBibId(sourceRecordId, List.of(
			SierraItem.builder()
				.id("47564655")
				.locationCode("example-location")
				.locationName("Example Location")
				.build()));

		// Act
		final var report = liveAvailabilityApiClient.getAvailabilityReport(clusterRecordId);

		// Assert
		assertThat(report, is(notNullValue()));

		final var items = report.getItemList();

		assertThat(items, is(notNullValue()));
		assertThat(items.size(), is(1));
		assertThat(report.getClusteredBibId(), is(clusterRecordId));

		final var firstItem = items.get(0);

		assertThat(firstItem, is(notNullValue()));
		assertThat(firstItem.getId(), is("47564655"));

		assertThat(firstItem.getLocation(), is(notNullValue()));
		assertThat(firstItem.getLocation().getCode(), is("example-location"));
		assertThat(firstItem.getLocation().getName(), is("Example Location"));

		assertThat(firstItem.getAgency().getCode(), is(nullValue()));
		assertThat(firstItem.getAgency().getDescription(), is(nullValue()));
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
		assertThat("Report is not null", report, is(notNullValue()));
		assertThat("Should have cluster record ID",
			report.getClusteredBibId(), is(clusterRecordId));

		assertThat("Should contain no items", report.getItemList(),  is(nullValue()));
		assertThat("Should contain no errors", report.getErrors(),  is(nullValue()));
	}
}
