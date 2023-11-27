package org.olf.dcb.api;

import static io.micronaut.http.HttpStatus.BAD_REQUEST;
import static io.micronaut.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockserver.client.MockServerClient;
import org.olf.dcb.core.interaction.sierra.SierraItemsAPIFixture;
import org.olf.dcb.core.model.DataAgency;
import org.olf.dcb.test.AgencyFixture;
import org.olf.dcb.test.BibRecordFixture;
import org.olf.dcb.test.ClusterRecordFixture;
import org.olf.dcb.test.HostLmsFixture;
import org.olf.dcb.test.ReferenceValueMappingFixture;

import io.micronaut.core.io.ResourceLoader;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import jakarta.inject.Inject;
import lombok.SneakyThrows;
import services.k_int.interaction.sierra.SierraTestUtils;
import services.k_int.test.mockserver.MockServerMicronautTest;

@MockServerMicronautTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LiveAvailabilityApiTests {
	private static final String HOST_LMS_CODE = "live-availability-api-tests";

	@Inject
	private ResourceLoader loader;

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

	@BeforeAll
	@SneakyThrows
	public void beforeAll(MockServerClient mock) {
		final String TOKEN = "test-token";
		final String BASE_URL = "https://live-availability-api-tests.com";
		final String KEY = "live-availability-key";
		final String SECRET = "live-availability-secret";

		SierraTestUtils.mockFor(mock, BASE_URL)
			.setValidCredentials(KEY, SECRET, TOKEN, 60);

		final var sierraItemsAPIFixture = new SierraItemsAPIFixture(mock, loader);

		sierraItemsAPIFixture.twoItemsResponseForBibId("798472");

		hostLmsFixture.deleteAll();

		hostLmsFixture.createSierraHostLms(HOST_LMS_CODE, KEY, SECRET, BASE_URL, "item");
	}

	@BeforeEach
	void beforeEach() {
		clusterRecordFixture.deleteAll();
	}

	@Test
	void canProvideAListOfAvailableItems() {
		// Arrange
		final var clusterRecordId = randomUUID();

		final var clusterRecord = clusterRecordFixture.createClusterRecord(clusterRecordId);

		final var hostLms = hostLmsFixture.findByCode(HOST_LMS_CODE);

		final var sourceSystemId = hostLms.getId();

		bibRecordFixture.createBibRecord(randomUUID(), sourceSystemId,
			"798472", clusterRecord);

		referenceValueMappingFixture.defineLocationToAgencyMapping( "live-availability-api-tests", "ab6", "345test");

		agencyFixture.saveAgency(
			DataAgency.builder()
				.id(randomUUID())
				.code("345test")
				.name("Test College")
				.build());

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
		assertThat(firstItem.getAgency().getCode(), is("345test"));
		assertThat(firstItem.getAgency().getDescription(), is("Test College"));

		final var firstItemStatus = firstItem.getStatus();

		assertThat(firstItemStatus, is(notNullValue()));
		assertThat(firstItemStatus.getCode(), is("AVAILABLE"));

		final var firstItemLocation = firstItem.getLocation();

		assertThat(firstItemLocation, is(notNullValue()));
		assertThat(firstItemLocation.getCode(), is("ab6"));
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
		assertThat(secondItem.getAgency().getCode(), is("345test"));
		assertThat(secondItem.getAgency().getDescription(), is("Test College"));

		final var secondItemStatus = secondItem.getStatus();

		assertThat(secondItemStatus, is(notNullValue()));
		assertThat(secondItemStatus.getCode(), is("CHECKED_OUT"));

		final var secondItemLocation = secondItem.getLocation();

		assertThat(secondItemLocation, is(notNullValue()));
		assertThat(secondItemLocation.getCode(), is("ab6"));
		assertThat(secondItemLocation.getName(), is("King 6th Floor"));
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

		final var clusterRecord = clusterRecordFixture.createClusterRecord(clusterRecordId);

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

		clusterRecordFixture.createClusterRecord(clusterRecordId);

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
