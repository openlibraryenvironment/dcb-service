package org.olf.dcb.request.resolution;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.hamcrest.Matcher;
import org.junit.jupiter.api.*;
import org.mockserver.client.MockServerClient;
import org.olf.dcb.core.interaction.sierra.SierraApiFixtureProvider;
import org.olf.dcb.core.interaction.sierra.SierraItem;
import org.olf.dcb.core.interaction.sierra.SierraItemsAPIFixture;
import org.olf.dcb.core.model.*;
import org.olf.dcb.core.model.clustering.ClusterRecord;
import org.olf.dcb.test.*;
import services.k_int.interaction.sierra.FixedField;
import services.k_int.interaction.sierra.SierraTestUtils;
import services.k_int.test.mockserver.MockServerMicronautTest;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasProperty;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.olf.dcb.core.model.FunctionalSettingType.SELECT_UNAVAILABLE_ITEMS;
import static org.olf.dcb.core.model.PatronRequest.Status.PATRON_VERIFIED;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;
import static org.olf.dcb.test.matchers.ItemMatchers.*;

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
	@Inject private SupplierRequestsFixture supplierRequestsFixture;
	@Inject private PatronRequestsFixture patronRequestsFixture;
	@Inject private PatronFixture patronFixture;
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
		supplierRequestsFixture.deleteAll();
		patronRequestsFixture.deleteAll();
		patronFixture.deleteAllPatrons();
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
	void shouldSelectItemWithBestGeographicProximityWhenDueDatesAreSame() {
		// Arrange
		final var bibRecordId = UUID.randomUUID();
		final var sourceRecordId = "465675";
		final var clusterRecord = createClusterAndBibRecord(bibRecordId, sourceRecordId);
		final var pickupLocation = definePickupLocationAtRoyalAlbertDock();
		final var patron = definePatron("872321", "home-library");
		final var patronRequest = createPatronRequest(patron, clusterRecord, pickupLocation);

		// Enable consortium setting for unavailable items
		consortiumFixture.createConsortiumWithFunctionalSetting(SELECT_UNAVAILABLE_ITEMS, true);

		// Create test items with different locations
		final var itemAId = "651463";
		final var itemABarcode = "76653672456";
		final var itemALocationCode = "A";
		final var marbleArchAgency = defineAgencyLocatedAtMarbleArch("marble-arch");
		referenceValueMappingFixture.defineLocationToAgencyMapping(
			CATALOGUING_HOST_LMS_CODE, itemALocationCode, marbleArchAgency.getCode());

		final var itemBId = "372656";
		final var itemBBarcode = "6256486473634";
		final var itemBLocationCode = "B";
		final var chatsworthAgency = defineAgencyLocatedAtChatsworth("chatsworth");
		referenceValueMappingFixture.defineLocationToAgencyMapping(
			CATALOGUING_HOST_LMS_CODE, itemBLocationCode, chatsworthAgency.getCode());

		sierraItemsAPIFixture.itemsForBibId(sourceRecordId, List.of(
			createCheckedOutItem(itemAId, itemABarcode, itemALocationCode),
			createCheckedOutItem(itemBId, itemBBarcode, itemBLocationCode)
		));

		// Act
		final var resolution = resolve(patronRequest);

		// Assert
		assertThat(resolution, allOf(
			notNullValue(),
			hasChosenItem(
				hasHostLmsCode(CIRCULATING_HOST_LMS_CODE),
				hasLocalId("372656"),
				hasBarcode("6256486473634"),
				hasLocalBibId("465675"),
				hasLocationCode("B"),
				hasAgencyCode("chatsworth")
			)
		));;
	}

	private ClusterRecord createClusterAndBibRecord(UUID bibRecordId, String sourceRecordId) {
		final var clusterRecord = clusterRecordFixture.createClusterRecord(UUID.randomUUID(), bibRecordId);
		bibRecordFixture.createBibRecord(bibRecordId, cataloguingHostLms.getId(), sourceRecordId, clusterRecord);

		return clusterRecord;
	}

	private PatronRequest createPatronRequest(Patron patron, ClusterRecord clusterRecord, Location pickupLocation) {
		PatronRequest patronRequest = PatronRequest.builder()
			.id(UUID.randomUUID())
			.patron(patron)
			.bibClusterId(clusterRecord.getId())
			.pickupLocationCodeContext(BORROWING_HOST_LMS_CODE)
			.pickupLocationCode(String.valueOf(pickupLocation.getId()))
			.status(PATRON_VERIFIED)
			.patronHostlmsCode(BORROWING_HOST_LMS_CODE)
			.build();

		return patronRequestsFixture.savePatronRequest(patronRequest);
	}

	private SierraItem createCheckedOutItem(String id, String barcode, String locationCode) {
		return SierraItem.builder()
				.id(id)
				.barcode(barcode)
				.statusCode("-")
				.dueDate(Instant.parse("2024-12-15T00:00:00Z"))
				.itemType("1")
				.fixedFields(Map.of(61, FixedField.builder().value("1").build()))
				.locationCode(locationCode)
				.build();
	}

	private Resolution resolve(PatronRequest patronRequest) {
		return singleValueFrom(patronRequestResolutionService.resolvePatronRequest(patronRequest));
	}

	private Patron definePatron(String localId, String homeLibraryCode) {
		return patronFixture.definePatron(localId, homeLibraryCode,
			cataloguingHostLms, agencyFixture.findByCode(BORROWING_AGENCY_CODE));
	}

	private Matcher<Resolution> hasChosenItem(Matcher<Item>... matchers) {
		return hasProperty("chosenItem", allOf(matchers));
	}

	private DataAgency defineAgencyLocatedAtChatsworth(String code) {
		return agencyFixture.defineAgency(code, "Test Agency 1",
			hostLmsFixture.findByCode(CIRCULATING_HOST_LMS_CODE),
			53.227558, -1.611566);
	}

	private DataAgency defineAgencyLocatedAtMarbleArch(String code) {
		return agencyFixture.defineAgency(code, "Test Agency 1",
			hostLmsFixture.findByCode(CIRCULATING_HOST_LMS_CODE),
			51.513222, -0.159015);
	}

	private Location definePickupLocationAtRoyalAlbertDock() {
		return locationFixture.createPickupLocation(
			"Pickup Location", "pickup-location", 53.399433, -2.992117);
	}
}
