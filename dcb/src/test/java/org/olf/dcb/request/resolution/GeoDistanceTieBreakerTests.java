package org.olf.dcb.request.resolution;

import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasProperty;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.olf.dcb.core.model.FunctionalSettingType.SELECT_UNAVAILABLE_ITEMS;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;
import static org.olf.dcb.test.matchers.ItemMatchers.hasBarcode;
import static org.olf.dcb.utils.PropertyAccessUtils.getValueOrNull;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.hamcrest.Matcher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockserver.client.MockServerClient;
import org.olf.dcb.core.interaction.sierra.SierraApiFixtureProvider;
import org.olf.dcb.core.interaction.sierra.SierraItem;
import org.olf.dcb.core.interaction.sierra.SierraItemsAPIFixture;
import org.olf.dcb.core.model.DataAgency;
import org.olf.dcb.core.model.DataHostLms;
import org.olf.dcb.core.model.Item;
import org.olf.dcb.core.model.Location;
import org.olf.dcb.core.clustering.model.ClusterRecord;
import org.olf.dcb.test.AgencyFixture;
import org.olf.dcb.test.BibRecordFixture;
import org.olf.dcb.test.ClusterRecordFixture;
import org.olf.dcb.test.ConsortiumFixture;
import org.olf.dcb.test.HostLmsFixture;
import org.olf.dcb.test.LocationFixture;
import org.olf.dcb.test.ReferenceValueMappingFixture;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import services.k_int.interaction.sierra.FixedField;
import services.k_int.interaction.sierra.SierraTestUtils;
import services.k_int.test.mockserver.MockServerMicronautTest;

@Slf4j
@MockServerMicronautTest
@TestInstance(PER_CLASS)
@MicronautTest(propertySources = "classpath:geo-sort-strategy-config.yml")
public class GeoDistanceTieBreakerTests {

	private static final String CATALOGUING_HOST_LMS_CODE = "resolution-cataloguing";
	private static final String CIRCULATING_HOST_LMS_CODE = "resolution-circulating";
	private static final String BORROWING_HOST_LMS_CODE = "resolution-borrowing";

	private static final String SUPPLYING_AGENCY_CODE = "supplying-agency";
	private static final String BORROWING_AGENCY_CODE = "borrowing-agency";
	private static final String PICKUP_LOCATION_CODE = "pickup-location";

	private static final String HOST_LMS_BASE_URL = "https://resolution-service-tests.com";
	private static final String HOST_LMS_TOKEN = "resolution-system-token";
	private static final String HOST_LMS_KEY = "resolution-system-key";
	private static final String HOST_LMS_SECRET = "resolution-system-secret";

	@Inject private PatronRequestResolutionService patronRequestResolutionService;
	@Inject private SierraApiFixtureProvider sierraApiFixtureProvider;
	@Inject private ClusterRecordFixture clusterRecordFixture;
	@Inject private BibRecordFixture bibRecordFixture;
	@Inject private HostLmsFixture hostLmsFixture;
	@Inject private ReferenceValueMappingFixture referenceValueMappingFixture;
	@Inject private AgencyFixture agencyFixture;
	@Inject private ConsortiumFixture consortiumFixture;
	@Inject private LocationFixture locationFixture;

	private SierraItemsAPIFixture sierraItemsAPIFixture;
	private DataHostLms cataloguingHostLms;

	@BeforeAll
	@SneakyThrows
	public void setupMockServerAndHostLms(MockServerClient mockServerClient) {
		SierraTestUtils.mockFor(mockServerClient, HOST_LMS_BASE_URL)
			.setValidCredentials(HOST_LMS_KEY, HOST_LMS_SECRET, HOST_LMS_TOKEN, 60);

		sierraItemsAPIFixture = sierraApiFixtureProvider.itemsApiFor(mockServerClient);

		cleanupExistingData();
		createHostLmsSystems();
	}

	private void cleanupExistingData() {
		hostLmsFixture.deleteAll();
		consortiumFixture.deleteAll();
	}

	private void createHostLmsSystems() {
		// Create cataloguing host LMS
		hostLmsFixture.createSierraHostLms(CATALOGUING_HOST_LMS_CODE, HOST_LMS_KEY,
			HOST_LMS_SECRET, HOST_LMS_BASE_URL, "item");
		cataloguingHostLms = hostLmsFixture.findByCode(CATALOGUING_HOST_LMS_CODE);

		// Create circulating host LMS
		hostLmsFixture.createSierraHostLms(CIRCULATING_HOST_LMS_CODE, "",
			"", "http://some-system", "item");

		// Create borrowing host LMS
		hostLmsFixture.createSierraHostLms(BORROWING_HOST_LMS_CODE, HOST_LMS_KEY,
			HOST_LMS_SECRET, HOST_LMS_BASE_URL, "item");
	}

	@BeforeEach
	void setupTestEnvironment() {
		clusterRecordFixture.deleteAll();
		bibRecordFixture.deleteAll();
		referenceValueMappingFixture.deleteAll();
		agencyFixture.deleteAll();

		setupLocationAndAgencyMappings();
	}

	private void setupLocationAndAgencyMappings() {
		// Define location to agency mappings
		referenceValueMappingFixture.defineLocationToAgencyMapping(
			BORROWING_HOST_LMS_CODE, PICKUP_LOCATION_CODE, BORROWING_AGENCY_CODE);

		// Define agencies
		agencyFixture.defineAgency(SUPPLYING_AGENCY_CODE, SUPPLYING_AGENCY_CODE,
			hostLmsFixture.findByCode(CIRCULATING_HOST_LMS_CODE));

		agencyFixture.defineAgency(BORROWING_AGENCY_CODE, BORROWING_AGENCY_CODE,
			hostLmsFixture.findByCode(BORROWING_HOST_LMS_CODE));

		// Define item type mapping
		referenceValueMappingFixture.defineLocalToCanonicalItemTypeRangeMapping(
			cataloguingHostLms.getCode(), 1, 1, "loanable-item");
	}

	@AfterEach
	void cleanupConsortium() {
		consortiumFixture.deleteAll();
	}

	@Test
	void shouldSelectClosestItemWhenDueDatesAreSame() {
		// Arrange
		final var bibRecordId = UUID.randomUUID();
		final var sourceRecordId = "465675";
		final var clusterRecord = createClusterAndBibRecord(bibRecordId, sourceRecordId);
		final var pickupLocation = definePickupLocationAtRoyalAlbertDock();

		// Enable consortium setting for unavailable items
		consortiumFixture.createConsortiumWithFunctionalSetting(SELECT_UNAVAILABLE_ITEMS, true);

		// Chatsworth is closer to the borrowing agency than Marble Arch
		final var marbleArchAgency = defineAgencyLocatedAtMarbleArch();
		final var chatsworthAgency = defineAgencyLocatedAtChatsworth();

		// Create test items with different locations
		final var furthestAwayItemId = "651463";
		final var furthestAwayItemBarcode = "76653672456";
		final var furtherAwayItemLocationCode = "A";

		referenceValueMappingFixture.defineLocationToAgencyMapping(
			CATALOGUING_HOST_LMS_CODE, furtherAwayItemLocationCode, marbleArchAgency.getCode());

		final var closestItemId = "372656";
		final var closestItemBarcode = "6256486473634";
		final var closestItemLocationCode = "B";

		referenceValueMappingFixture.defineLocationToAgencyMapping(
			CATALOGUING_HOST_LMS_CODE, closestItemLocationCode, chatsworthAgency.getCode());

		final var sameDueDate = Instant.parse("2024-12-15T00:00:00Z");

		sierraItemsAPIFixture.itemsForBibId(sourceRecordId, List.of(
			createCheckedOutItem(furthestAwayItemId, furthestAwayItemBarcode,
				furtherAwayItemLocationCode, sameDueDate),
			createCheckedOutItem(closestItemId, closestItemBarcode,
				closestItemLocationCode, sameDueDate)
		));

		// Act
		final var resolution = resolve(clusterRecord, pickupLocation);

		// Assert
		assertThat(resolution, allOf(
			notNullValue(),
			hasChosenItem(hasBarcode(closestItemBarcode))
		));
	}

	@Test
	void shouldSelectEarliestDueDateIrrespectiveOfGeographicProximity() {
		// Arrange
		final var bibRecordId = UUID.randomUUID();
		final var sourceRecordId = "465675";
		final var clusterRecord = createClusterAndBibRecord(bibRecordId, sourceRecordId);
		final var pickupLocation = definePickupLocationAtRoyalAlbertDock();

		// Enable consortium setting for unavailable items
		consortiumFixture.createConsortiumWithFunctionalSetting(SELECT_UNAVAILABLE_ITEMS, true);

		// Chatsworth is closer to the borrowing agency than Marble Arch
		final var marbleArchAgency = defineAgencyLocatedAtMarbleArch();
		final var chatsworthAgency = defineAgencyLocatedAtChatsworth();

		// Create test items with different locations
		final var furthestAwayItemId = "651463";
		final var furthestAwayItemBarcode = "76653672456";
		final var furtherAwayItemLocationCode = "A";
		// This is an overdue item, with a zero hold count.
		// So it's going to have an availability date of "Now" + one default loan period
		final var furthestAwayItemDueDate = Instant.now().plus(28, ChronoUnit.DAYS);

		referenceValueMappingFixture.defineLocationToAgencyMapping(
			CATALOGUING_HOST_LMS_CODE, furtherAwayItemLocationCode, marbleArchAgency.getCode());

		final var closestItemId = "372656";
		final var closestItemBarcode = "6256486473634";
		final var closestItemLocationCode = "B";

		// This is not an overdue item. Hence, it should be after the due date of the furthest away item
		// 84 days to be sure (3 default loan periods)
		final var closestItemDueDate = Instant.now().plus(84, ChronoUnit.DAYS);

		referenceValueMappingFixture.defineLocationToAgencyMapping(
			CATALOGUING_HOST_LMS_CODE, closestItemLocationCode, chatsworthAgency.getCode());
		sierraItemsAPIFixture.itemsForBibId(sourceRecordId, List.of(
			createCheckedOutItem(furthestAwayItemId, furthestAwayItemBarcode,
				furtherAwayItemLocationCode, furthestAwayItemDueDate),
			createCheckedOutItem(closestItemId, closestItemBarcode,
				closestItemLocationCode, closestItemDueDate)
		));

		// Act
		final var resolution = resolve(clusterRecord, pickupLocation
		);

		// Assert
		assertThat(resolution, allOf(
			notNullValue(),
			hasChosenItem(hasBarcode(furthestAwayItemBarcode))
		));
	}

	private ClusterRecord createClusterAndBibRecord(UUID bibRecordId, String sourceRecordId) {
		final var clusterRecord = clusterRecordFixture.createClusterRecord(UUID.randomUUID(), bibRecordId);
		bibRecordFixture.createBibRecord(bibRecordId, cataloguingHostLms.getId(), sourceRecordId, clusterRecord);

		return clusterRecord;
	}

	private SierraItem createCheckedOutItem(String id, String barcode, String locationCode, Instant dueDate) {
		return SierraItem.builder()
				.id(id)
				.barcode(barcode)
				.statusCode("-")
				.dueDate(dueDate)
				.itemType("1")
				.fixedFields(Map.of(61, FixedField.builder().value("1").build()))
				.locationCode(locationCode)
				.build();
	}

	private Resolution resolve(ClusterRecord clusterRecord, Location pickupLocation) {
		return singleValueFrom(patronRequestResolutionService.resolve(
			ResolutionParameters.builder()
				.borrowingAgencyCode(BORROWING_AGENCY_CODE)
				.borrowingHostLmsCode(BORROWING_HOST_LMS_CODE)
				.pickupAgencyCode(BORROWING_AGENCY_CODE)
				.bibClusterId(clusterRecord.getId())
				// This is due to geo-proximity interpreting the location code parameter as the ID
				.pickupLocationCode(getValueOrNull(pickupLocation, Location::getId, UUID::toString))
				.build()));
	}

	@SafeVarargs
	private Matcher<Resolution> hasChosenItem(Matcher<Item>... matchers) {
		return hasProperty("chosenItem", allOf(matchers));
	}

	private DataAgency defineAgencyLocatedAtChatsworth() {
		return agencyFixture.defineAgency("chatsworth", "Test Agency 1",
			hostLmsFixture.findByCode(CIRCULATING_HOST_LMS_CODE),
			53.227558, -1.611566);
	}

	private DataAgency defineAgencyLocatedAtMarbleArch() {
		return agencyFixture.defineAgency("marble-arch", "Test Agency 1",
			hostLmsFixture.findByCode(CIRCULATING_HOST_LMS_CODE),
			51.513222, -0.159015);
	}

	private Location definePickupLocationAtRoyalAlbertDock() {
		return locationFixture.createPickupLocation(
			"Pickup Location", "pickup-location", 53.399433, -2.992117);
	}
}
