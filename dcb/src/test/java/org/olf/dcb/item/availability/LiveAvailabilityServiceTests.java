package org.olf.dcb.item.availability;

import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockserver.client.MockServerClient;
import org.olf.dcb.core.interaction.sierra.SierraItemsAPIFixture;
import org.olf.dcb.core.model.DataHostLms;
import org.olf.dcb.core.model.Item;
import org.olf.dcb.test.BibRecordFixture;
import org.olf.dcb.test.ClusterRecordFixture;
import org.olf.dcb.test.HostLmsFixture;

import io.micronaut.core.io.ResourceLoader;
import jakarta.inject.Inject;
import lombok.SneakyThrows;
import services.k_int.interaction.sierra.SierraTestUtils;
import services.k_int.test.mockserver.MockServerMicronautTest;

@MockServerMicronautTest
@TestInstance(PER_CLASS)
class LiveAvailabilityServiceTests {
	@Inject
	private ResourceLoader loader;

	@Inject
	private ClusterRecordFixture clusterRecordFixture;
	@Inject
	private BibRecordFixture bibRecordFixture;
	@Inject
	private HostLmsFixture hostLmsFixture;

	@Inject
	private LiveAvailabilityService liveAvailabilityService;

	private DataHostLms firstHostLms;
	private DataHostLms secondHostLms;

	@BeforeAll
	@SneakyThrows
	public void beforeAll(MockServerClient mock) {
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

		SierraTestUtils.mockFor(mock, FIRST_HOST_LMS_BASE_URL)
			.setValidCredentials(FIRST_HOST_LMS_KEY, FIRST_HOST_LMS_SECRET, FIRST_HOST_LMS_TOKEN, 60);

		SierraTestUtils.mockFor(mock, SECOND_HOST_LMS_BASE_URL)
			.setValidCredentials(SECOND_SYSTEM_KEY, SECOND_SYSTEM_SECRET, SECOND_SYSTEM_TOKEN, 60);

		hostLmsFixture.deleteAll();

		firstHostLms = hostLmsFixture.createSierraHostLms(FIRST_HOST_LMS_CODE,
			FIRST_HOST_LMS_KEY, FIRST_HOST_LMS_SECRET, FIRST_HOST_LMS_BASE_URL, "item");

		secondHostLms = hostLmsFixture.createSierraHostLms(SECOND_HOST_LMS_CODE,
			SECOND_SYSTEM_KEY, SECOND_SYSTEM_SECRET, SECOND_HOST_LMS_BASE_URL, "item");
	}

	@BeforeEach
	void beforeEach() {
		clusterRecordFixture.deleteAll();
	}

	@Test
	@DisplayName("Should get items for multiple bibs from separate Sierra systems")
	void shouldGetItemsForMultipleBibsFromSeparateSierraSystems(MockServerClient mock) {
		// Arrange
		final var clusterRecord = clusterRecordFixture.createClusterRecord(randomUUID());

		bibRecordFixture.createBibRecord(randomUUID(), firstHostLms.getId(),
			"465675", clusterRecord);

		bibRecordFixture.createBibRecord(randomUUID(), secondHostLms.getId(),
			"767648", clusterRecord);

		final var sierraItemsAPIFixture = new SierraItemsAPIFixture(mock, loader);

		sierraItemsAPIFixture.twoItemsResponseForBibId("465675");
		sierraItemsAPIFixture.threeItemsResponseForBibId("767648");

		// Act
		final var report = liveAvailabilityService
			.getAvailableItems(clusterRecord.getId()).block();

		// Assert
		assertThat("Report should not be null", report, is(notNullValue()));

		final var items = report.getItems();

		assertThat("Report should include items from both systems", items, is(hasSize(5)));

		final var itemBarcodes = items.stream()
			.map(Item::getBarcode)
			.toList();

		// This is a compromise that checks the rough identity of each item
		// without duplicating checks for many fields
		// The order is important due to the sorting applied to items
		assertThat("Should contain expected items sorted by location and call number",
			itemBarcodes, contains(
				"9849123490", "6565750674", "30800005238487", "30800005208449", "30800005315459"));
	}

	@Test
	@DisplayName("Should report zero items when Sierra responds with no records found error")
	void shouldReportZeroItemsWhenSierraRespondsWithNoRecordsFoundError(MockServerClient mock) {
		// Arrange
		final var clusterRecord = clusterRecordFixture.createClusterRecord(randomUUID());

		bibRecordFixture.createBibRecord(randomUUID(), firstHostLms.getId(),
			"762354", clusterRecord);

		final var sierraItemsAPIFixture = new SierraItemsAPIFixture(mock, loader);

		sierraItemsAPIFixture.zeroItemsResponseForBibId("762354");

		// Act
		final var report = liveAvailabilityService
			.getAvailableItems(clusterRecord.getId()).block();

		// Assert
		assertThat("Report should not be null", report, is(notNullValue()));
		assertThat("No items should be included in report",
			report.getItems(), is(hasSize(0)));
	}

	@Test
	@DisplayName("Should report failures when fetching items from Sierra")
	void shouldReportFailuresFetchingItemsFromSierra(MockServerClient mock) {
		// Arrange
		final var clusterRecord = clusterRecordFixture.createClusterRecord(randomUUID());

		bibRecordFixture.createBibRecord(randomUUID(), firstHostLms.getId(),
			"839552", clusterRecord);

		final var sierraItemsAPIFixture = new SierraItemsAPIFixture(mock, loader);

		sierraItemsAPIFixture.errorResponseForBibId("839552");

		// Act
		final var report = liveAvailabilityService
			.getAvailableItems(clusterRecord.getId()).block();

		// Assert
		assertThat("Report should not be null", report, is(notNullValue()));

		final var errors = report.getErrors();

		assertThat("Reported errors should not be null", errors, is(notNullValue()));
		assertThat("Should have one error", errors, hasSize(1));
		assertThat("Should have one error", errors.get(0).getMessage(),
			is("Failed to fetch items for bib: 839552 from host: first-local-system"));
	}

	@Test
	@DisplayName("Should find no items when no bibs in cluster record")
	void shouldFindNoItemsWhenNoBibsInClusterRecord() {
		// Arrange
		final var clusterRecord = clusterRecordFixture.createClusterRecord(randomUUID());

		// Act
		final var report = liveAvailabilityService
			.getAvailableItems(clusterRecord.getId()).block();

		// Assert
		assertThat("Report should not be null", report, is(notNullValue()));
		assertThat("Items returned should not be null", report.getItems(), is(notNullValue()));
		assertThat("Should have no items", report.getItems(), hasSize(0));
	}
}
