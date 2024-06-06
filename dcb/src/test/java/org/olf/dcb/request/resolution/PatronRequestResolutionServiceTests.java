package org.olf.dcb.request.resolution;

import static java.time.temporal.ChronoUnit.HOURS;
import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasProperty;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.olf.dcb.core.model.PatronRequest.Status.PATRON_VERIFIED;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;
import static org.olf.dcb.test.matchers.ItemMatchers.hasAgencyCode;
import static org.olf.dcb.test.matchers.ItemMatchers.hasBarcode;
import static org.olf.dcb.test.matchers.ItemMatchers.hasHostLmsCode;
import static org.olf.dcb.test.matchers.ItemMatchers.hasLocalBibId;
import static org.olf.dcb.test.matchers.ItemMatchers.hasLocalId;
import static org.olf.dcb.test.matchers.ItemMatchers.hasLocationCode;

import java.time.Instant;
import java.util.List;

import org.hamcrest.Matcher;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockserver.client.MockServerClient;
import org.olf.dcb.core.interaction.sierra.SierraApiFixtureProvider;
import org.olf.dcb.core.interaction.sierra.SierraItem;
import org.olf.dcb.core.interaction.sierra.SierraItemsAPIFixture;
import org.olf.dcb.core.model.DataHostLms;
import org.olf.dcb.core.model.Item;
import org.olf.dcb.core.model.Patron;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.test.AgencyFixture;
import org.olf.dcb.test.BibRecordFixture;
import org.olf.dcb.test.ClusterRecordFixture;
import org.olf.dcb.test.HostLmsFixture;
import org.olf.dcb.test.PatronFixture;
import org.olf.dcb.test.PatronRequestsFixture;
import org.olf.dcb.test.ReferenceValueMappingFixture;
import org.olf.dcb.test.SupplierRequestsFixture;

import jakarta.inject.Inject;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import services.k_int.interaction.sierra.SierraTestUtils;
import services.k_int.test.mockserver.MockServerMicronautTest;

@Slf4j
@MockServerMicronautTest
@TestInstance(PER_CLASS)
class PatronRequestResolutionServiceTests {
	private final String CATALOGUING_HOST_LMS_CODE = "resolution-cataloguing";
	private final String CIRCULATING_HOST_LMS_CODE = "resolution-circulating";
	private final String BORROWING_HOST_LMS_CODE = "resolution-borrowing";

	private final String SUPPLYING_AGENCY_CODE = "supplying-agency";
	private final String BORROWING_AGENCY_CODE = "borrowing-agency";

	private final String PICKUP_LOCATION_CODE = "pickup-location";
	private final String ITEM_LOCATION_CODE = "item-location";

	@Inject
	private PatronRequestResolutionService patronRequestResolutionService;

	@Inject
	private SierraApiFixtureProvider sierraApiFixtureProvider;

	@Inject
	private ClusterRecordFixture clusterRecordFixture;
	@Inject
	private BibRecordFixture bibRecordFixture;
	@Inject
	private HostLmsFixture hostLmsFixture;
	@Inject
	private SupplierRequestsFixture supplierRequestsFixture;
	@Inject
	private PatronRequestsFixture patronRequestsFixture;
	@Inject
	private PatronFixture patronFixture;
	@Inject
	private ReferenceValueMappingFixture referenceValueMappingFixture;
	@Inject
	private AgencyFixture agencyFixture;

	private SierraItemsAPIFixture sierraItemsAPIFixture;

	private DataHostLms cataloguingHostLms;

	@BeforeAll
	@SneakyThrows
	public void beforeAll(MockServerClient mockServerClient) {
		final String HOST_LMS_BASE_URL = "https://resolution-service-tests.com";
		final String HOST_LMS_TOKEN = "resolution-system-token";
		final String HOST_LMS_KEY = "resolution-system-key";
		final String HOST_LMS_SECRET = "resolution-system-secret";

		SierraTestUtils.mockFor(mockServerClient, HOST_LMS_BASE_URL)
			.setValidCredentials(HOST_LMS_KEY, HOST_LMS_SECRET, HOST_LMS_TOKEN, 60);

		sierraItemsAPIFixture = sierraApiFixtureProvider.itemsApiFor(mockServerClient);

		supplierRequestsFixture.deleteAll();
		patronRequestsFixture.deleteAll();
		patronFixture.deleteAllPatrons();

		hostLmsFixture.deleteAll();

		hostLmsFixture.createSierraHostLms(CATALOGUING_HOST_LMS_CODE, HOST_LMS_KEY,
			HOST_LMS_SECRET, HOST_LMS_BASE_URL, "item");

		cataloguingHostLms = hostLmsFixture.findByCode(CATALOGUING_HOST_LMS_CODE);

		hostLmsFixture.createSierraHostLms(CIRCULATING_HOST_LMS_CODE, "",
			"", "http://some-system", "item");

		hostLmsFixture.createSierraHostLms(BORROWING_HOST_LMS_CODE, "",
			"", "http://some-system", "item");
	}

	@BeforeEach
	void beforeEach() {
		clusterRecordFixture.deleteAll();
		referenceValueMappingFixture.deleteAll();
		agencyFixture.deleteAll();

		referenceValueMappingFixture.defineLocationToAgencyMapping(
			BORROWING_HOST_LMS_CODE, PICKUP_LOCATION_CODE, BORROWING_AGENCY_CODE);

		referenceValueMappingFixture.defineLocationToAgencyMapping(
			CATALOGUING_HOST_LMS_CODE, ITEM_LOCATION_CODE, SUPPLYING_AGENCY_CODE);

		agencyFixture.defineAgency(SUPPLYING_AGENCY_CODE, SUPPLYING_AGENCY_CODE,
			hostLmsFixture.findByCode(CIRCULATING_HOST_LMS_CODE));

		agencyFixture.defineAgency(BORROWING_AGENCY_CODE, BORROWING_AGENCY_CODE,
			hostLmsFixture.findByCode(BORROWING_HOST_LMS_CODE));
	}

	@Test
	void shouldChooseFirstAvailableItem() {
		// Arrange
		final var bibRecordId = randomUUID();

		final var clusterRecord = clusterRecordFixture.createClusterRecord(randomUUID(), bibRecordId);

		final var sourceRecordId = "465675";

		bibRecordFixture.createBibRecord(bibRecordId, cataloguingHostLms.getId(),
				sourceRecordId, clusterRecord);

		final var onlyAvailableItemId = "651463";
		final var onlyAvailableItemBarcode = "76653672456";

		sierraItemsAPIFixture.itemsForBibId(sourceRecordId, List.of(
			// Sierra item with due date is considered not available
			CheckedOutItem("372656", "6256486473634"),
			availableItem(onlyAvailableItemId, onlyAvailableItemBarcode)
		));

		final var homeLibraryCode = "home-library";

		final var patron = definePatron("872321", homeLibraryCode);

		var patronRequest = PatronRequest.builder()
			.id(randomUUID())
			.patron(patron)
			.bibClusterId(clusterRecord.getId())
			.pickupLocationCodeContext(BORROWING_HOST_LMS_CODE)
			.pickupLocationCode(PICKUP_LOCATION_CODE)
			.status(PATRON_VERIFIED)
			.build();

		patronRequestsFixture.savePatronRequest(patronRequest);

		// Act
		final var resolution = resolve(patronRequest);

		// Assert
		assertThat(resolution, allOf(
			notNullValue(),
			hasChosenItem(
					hasHostLmsCode(CIRCULATING_HOST_LMS_CODE),
					hasLocalId(onlyAvailableItemId),
					hasBarcode(onlyAvailableItemBarcode),
					hasLocalBibId(sourceRecordId),
					hasLocationCode(ITEM_LOCATION_CODE),
					hasAgencyCode(SUPPLYING_AGENCY_CODE)
			)));
	}

	@Test
	void shouldSelectManuallySelectedItem() {
		// Arrange
		final var bibRecordId = randomUUID();

		final var clusterRecord = clusterRecordFixture.createClusterRecord(randomUUID(), bibRecordId);

		final var sourceRecordId = "174625";

		bibRecordFixture.createBibRecord(bibRecordId, cataloguingHostLms.getId(),
			sourceRecordId, clusterRecord);

		final var onlyAvailableItemId = "826425";
		final var onlyAvailableItemBarcode = "25452553";

		sierraItemsAPIFixture.itemsForBibId(sourceRecordId, List.of(
			availableItem(onlyAvailableItemId, onlyAvailableItemBarcode)
		));

		final var homeLibraryCode = "home-library";

		final var patron = definePatron("872321", homeLibraryCode);

		var patronRequest = PatronRequest.builder()
			.id(randomUUID())
			.patron(patron)
			.bibClusterId(clusterRecord.getId())
			.pickupLocationCodeContext(BORROWING_HOST_LMS_CODE)
			.pickupLocationCode(PICKUP_LOCATION_CODE)
			.status(PATRON_VERIFIED)
			.isManuallySelectedItem(true)
			.localItemId(onlyAvailableItemId)
			// This has to be the Host LMS associated with the agency for the item
			.localItemHostlmsCode(CIRCULATING_HOST_LMS_CODE)
			.localItemAgencyCode(SUPPLYING_AGENCY_CODE)
			.build();

		patronRequestsFixture.savePatronRequest(patronRequest);

		// Act
		final var resolution = resolve(patronRequest);

		// Assert
		assertThat(resolution, allOf(
			notNullValue(),
			hasChosenItem(
				hasHostLmsCode(CIRCULATING_HOST_LMS_CODE),
				hasLocalId(onlyAvailableItemId),
				hasBarcode(onlyAvailableItemBarcode),
				hasLocalBibId(sourceRecordId),
				hasLocationCode(ITEM_LOCATION_CODE),
				hasAgencyCode(SUPPLYING_AGENCY_CODE)
			)));
	}

	private Resolution resolve(PatronRequest patronRequest) {
		return singleValueFrom(patronRequestResolutionService.resolvePatronRequest(patronRequest));
	}

	private Patron definePatron(String localId, String homeLibraryCode) {
		return patronFixture.definePatron(localId, homeLibraryCode,
			cataloguingHostLms, agencyFixture.findByCode(BORROWING_AGENCY_CODE));
	}

	private SierraItem availableItem(String id, String barcode) {
		return SierraItem.builder()
			.id(id)
			.barcode(barcode)
			.locationCode(ITEM_LOCATION_CODE)
			.statusCode("-")
			.build();
	}

	private SierraItem CheckedOutItem(String id, String barcode) {
		return SierraItem.builder()
			.id(id)
			.barcode(barcode)
			.locationCode(ITEM_LOCATION_CODE)
			.statusCode("-")
			.dueDate(Instant.now().plus(3, HOURS))
			.build();
	}

	@SafeVarargs
	private Matcher<Resolution> hasChosenItem(Matcher<Item>... matchers) {
		return hasProperty("chosenItem", allOf(matchers));
	}
}
