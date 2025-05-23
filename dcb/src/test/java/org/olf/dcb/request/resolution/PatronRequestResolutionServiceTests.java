package org.olf.dcb.request.resolution;

import static java.time.temporal.ChronoUnit.HOURS;
import static java.util.Collections.emptyList;
import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.olf.dcb.core.model.FunctionalSettingType.OWN_LIBRARY_BORROWING;
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
import java.util.Map;

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
import org.olf.dcb.core.model.FunctionalSettingType;
import org.olf.dcb.core.model.Item;
import org.olf.dcb.core.model.Patron;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.test.AgencyFixture;
import org.olf.dcb.test.BibRecordFixture;
import org.olf.dcb.test.ClusterRecordFixture;
import org.olf.dcb.test.ConsortiumFixture;
import org.olf.dcb.test.HostLmsFixture;
import org.olf.dcb.test.PatronFixture;
import org.olf.dcb.test.PatronRequestsFixture;
import org.olf.dcb.test.ReferenceValueMappingFixture;
import org.olf.dcb.test.SupplierRequestsFixture;

import io.micronaut.context.annotation.Property;
import jakarta.inject.Inject;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import services.k_int.interaction.sierra.FixedField;
import services.k_int.interaction.sierra.SierraTestUtils;
import services.k_int.test.mockserver.MockServerMicronautTest;

@Slf4j
@MockServerMicronautTest
@TestInstance(PER_CLASS)
@Property(name = "dcb.resolution.live-availability.timeout", value = "PT1S")
class PatronRequestResolutionServiceTests {
	private final String CATALOGUING_HOST_LMS_CODE = "resolution-cataloguing";
	private final String CIRCULATING_HOST_LMS_CODE = "resolution-circulating";
	private final String BORROWING_HOST_LMS_CODE = "resolution-borrowing";
	private final String SAME_SERVER_SUPPLYING_HOST_LMS_CODE = "same-server-hostlms";

	private final String SUPPLYING_AGENCY_CODE = "supplying-agency";
	private final String BORROWING_AGENCY_CODE = "borrowing-agency";
	private final String SAME_SERVER_AGENCY_CODE = "same-server-agency";

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
	@Inject
	private ConsortiumFixture consortiumFixture;

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

		hostLmsFixture.createSierraHostLms(BORROWING_HOST_LMS_CODE, HOST_LMS_KEY,
			HOST_LMS_SECRET, HOST_LMS_BASE_URL, "item");

		// creating a supplying hostlms that is on the same server as the borrower
		hostLmsFixture.createSierraHostLms(SAME_SERVER_SUPPLYING_HOST_LMS_CODE, HOST_LMS_KEY,
			HOST_LMS_SECRET, HOST_LMS_BASE_URL, "item");
	}

	@BeforeEach
	void beforeEach() {
		clusterRecordFixture.deleteAll();
		bibRecordFixture.deleteAll();
		referenceValueMappingFixture.deleteAll();
		agencyFixture.deleteAll();
		consortiumFixture.deleteAll();

		referenceValueMappingFixture.defineLocationToAgencyMapping(
			BORROWING_HOST_LMS_CODE, PICKUP_LOCATION_CODE, BORROWING_AGENCY_CODE);

		referenceValueMappingFixture.defineLocationToAgencyMapping(
			CATALOGUING_HOST_LMS_CODE, ITEM_LOCATION_CODE, SUPPLYING_AGENCY_CODE);

		agencyFixture.defineAgency(SUPPLYING_AGENCY_CODE, SUPPLYING_AGENCY_CODE,
			hostLmsFixture.findByCode(CIRCULATING_HOST_LMS_CODE));

		agencyFixture.defineAgency(BORROWING_AGENCY_CODE, BORROWING_AGENCY_CODE,
			hostLmsFixture.findByCode(BORROWING_HOST_LMS_CODE));

		// Needs to align with sierra item type responses
		// For simplicity all sierra item types are expected to be on 1 in this class
		referenceValueMappingFixture.defineLocalToCanonicalItemTypeRangeMapping(
			cataloguingHostLms.getCode(), 1, 1, "loanable-item");
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

		final var unavailableItemId = "372656";
		final var unavailableItemBarcode = "6256486473634";

		sierraItemsAPIFixture.itemsForBibId(sourceRecordId, List.of(
			CheckedOutItem(unavailableItemId, unavailableItemBarcode),
			availableItem(onlyAvailableItemId, onlyAvailableItemBarcode, ITEM_LOCATION_CODE)
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
			.patronHostlmsCode(BORROWING_HOST_LMS_CODE)
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
			),
			hasAllItems(
				allOf(
					hasLocalId(unavailableItemId),
					hasBarcode(unavailableItemBarcode)
				),
				allOf(
					hasLocalId(onlyAvailableItemId),
					hasBarcode(onlyAvailableItemBarcode)
				)
			),
			hasFilteredItems(
				allOf(
					hasLocalId(onlyAvailableItemId),
					hasBarcode(onlyAvailableItemBarcode)
				)
			)
		));
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
			availableItem(onlyAvailableItemId, onlyAvailableItemBarcode,
				ITEM_LOCATION_CODE)
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
			.patronHostlmsCode(BORROWING_HOST_LMS_CODE)
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
			)
		));
	}

	@Test
	void shouldExcludeUnavailableItem() {
		// Arrange
		final var bibRecordId = randomUUID();

		final var clusterRecord = clusterRecordFixture.createClusterRecord(randomUUID(), bibRecordId);

		final var sourceRecordId = "2656846";

		bibRecordFixture.createBibRecord(bibRecordId, cataloguingHostLms.getId(),
			sourceRecordId, clusterRecord);

		final var unavailableItemId = "372656";
		final var unavailableItemBarcode = "6256486473634";

		sierraItemsAPIFixture.itemsForBibId(sourceRecordId, List.of(
			CheckedOutItem(unavailableItemId, unavailableItemBarcode)
		));

		final var patron = definePatron("872321", "home-library");

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
		final Resolution resolution = resolve(patronRequest);

		// Assert
		assertThat(resolution, allOf(
			notNullValue(),
			hasNoChosenItem()
		));
	}

	@Test
	void shouldExcludeItemWhichAlreadyHasAlreadyBeenRequested() {
		// Arrange
		final var bibRecordId = randomUUID();

		final var clusterRecord = clusterRecordFixture.createClusterRecord(randomUUID(), bibRecordId);

		final var sourceRecordId = "174663256";

		bibRecordFixture.createBibRecord(bibRecordId, cataloguingHostLms.getId(),
			sourceRecordId, clusterRecord);

		final var alreadyRequestedItemId = "6736345";
		final var alreadyRequestedItemBarcode = "87265265673";

		sierraItemsAPIFixture.itemsForBibId(sourceRecordId, List.of(
			SierraItem.builder()
				.id(alreadyRequestedItemId)
				.barcode(alreadyRequestedItemBarcode)
				.locationCode(ITEM_LOCATION_CODE)
				.statusCode("-")
				.holdCount(1)
				.build()
		));

		final var patron = definePatron("274563", "home-library");

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
			hasNoChosenItem()
		));
	}

	@Test
	void shouldNotWaitForSlowResponseForAvailability() {
		// Arrange
		final var bibRecordId = randomUUID();

		final var clusterRecord = clusterRecordFixture.createClusterRecord(randomUUID(), bibRecordId);

		final var sourceRecordId = "465675";

		bibRecordFixture.createBibRecord(bibRecordId, cataloguingHostLms.getId(),
			sourceRecordId, clusterRecord);

		final var onlyAvailableItemId = "651463";
		final var onlyAvailableItemBarcode = "76653672456";

		final var unavailableItemId = "372656";
		final var unavailableItemBarcode = "6256486473634";

		sierraItemsAPIFixture.itemsForBibId(sourceRecordId, List.of(
			CheckedOutItem(unavailableItemId, unavailableItemBarcode),
			availableItem(onlyAvailableItemId, onlyAvailableItemBarcode, ITEM_LOCATION_CODE)
		), 2000);

		final var homeLibraryCode = "home-library";

		final var patron = definePatron("298485", homeLibraryCode);

		var patronRequest = PatronRequest.builder()
			.id(randomUUID())
			.patron(patron)
			.bibClusterId(clusterRecord.getId())
			.pickupLocationCodeContext(BORROWING_HOST_LMS_CODE)
			.pickupLocationCode(PICKUP_LOCATION_CODE)
			.status(PATRON_VERIFIED)
			.patronHostlmsCode(BORROWING_HOST_LMS_CODE)
			.build();

		patronRequestsFixture.savePatronRequest(patronRequest);

		// Act
		final var resolution = resolve(patronRequest);

		// Assert
		assertThat(resolution, allOf(
			notNullValue(),
			hasNoChosenItem()
		));
	}


	@Test
	void shouldExcludeItemFromSameAgencyAsBorrowerWhenSettingIsDisabledForConsortia() {
		// Arrange
		consortiumFixture.createConsortiumWithFunctionalSetting(OWN_LIBRARY_BORROWING, false);

		final var bibRecordId = randomUUID();

		final var clusterRecord = clusterRecordFixture.createClusterRecord(randomUUID(), bibRecordId);

		final var sourceRecordId = "632545";

		bibRecordFixture.createBibRecord(bibRecordId,
			hostLmsFixture.findByCode(BORROWING_HOST_LMS_CODE).getId(),
			sourceRecordId, clusterRecord);

		final var onlyAvailableItemId = "564325";
		final var onlyAvailableItemBarcode = "721425354";
		final var itemLocationCode = "borrowing-location";

		sierraItemsAPIFixture.itemsForBibId(sourceRecordId, List.of(
			availableItem(onlyAvailableItemId, onlyAvailableItemBarcode,
				itemLocationCode)
		));

		referenceValueMappingFixture.defineLocationToAgencyMapping(BORROWING_HOST_LMS_CODE,
			itemLocationCode, BORROWING_AGENCY_CODE);

		final var homeLibraryCode = "home-library";

		final var patron = definePatron("356425", homeLibraryCode);

		var patronRequest = PatronRequest.builder()
			.id(randomUUID())
			.patron(patron)
			.bibClusterId(clusterRecord.getId())
			.pickupLocationCodeContext(BORROWING_HOST_LMS_CODE)
			.pickupLocationCode(PICKUP_LOCATION_CODE)
			.status(PATRON_VERIFIED)
			.patronHostlmsCode(BORROWING_HOST_LMS_CODE)
			.build();

		patronRequestsFixture.savePatronRequest(patronRequest);

		// Act
		final var resolution = resolve(patronRequest);

		// Assert
		assertThat(resolution, allOf(
			notNullValue(),
			hasNoChosenItem()
		));
	}

	@Test
	void shouldIncludeItemFromSameAgencyAsBorrowerWhenSettingIsEnabledForConsortia() {
		// Arrange
		consortiumFixture.createConsortiumWithFunctionalSetting(OWN_LIBRARY_BORROWING, true);

		final var bibRecordId = randomUUID();

		final var clusterRecord = clusterRecordFixture.createClusterRecord(randomUUID(), bibRecordId);

		final var sourceRecordId = "4526453";

		bibRecordFixture.createBibRecord(bibRecordId,
			hostLmsFixture.findByCode(BORROWING_HOST_LMS_CODE).getId(),
			sourceRecordId, clusterRecord);

		final var onlyAvailableItemId = "254635";
		final var onlyAvailableItemBarcode = "174295773";
		final var itemLocationCode = "borrowing-location";

		sierraItemsAPIFixture.itemsForBibId(sourceRecordId, List.of(
			availableItem(onlyAvailableItemId, onlyAvailableItemBarcode,
				itemLocationCode)
		));

		referenceValueMappingFixture.defineLocationToAgencyMapping(BORROWING_HOST_LMS_CODE,
			itemLocationCode, BORROWING_AGENCY_CODE);

		final var homeLibraryCode = "home-library";

		final var patron = definePatron("254255", homeLibraryCode);

		var patronRequest = PatronRequest.builder()
			.id(randomUUID())
			.patron(patron)
			.bibClusterId(clusterRecord.getId())
			.pickupLocationCodeContext(BORROWING_HOST_LMS_CODE)
			.pickupLocationCode(PICKUP_LOCATION_CODE)
			.status(PATRON_VERIFIED)
			.patronHostlmsCode(BORROWING_HOST_LMS_CODE)
			.build();

		patronRequestsFixture.savePatronRequest(patronRequest);

		// Act
		final var resolution = resolve(patronRequest);

		// Assert
		assertThat(resolution, allOf(
			notNullValue(),
			hasChosenItem(
				hasLocalId(onlyAvailableItemId),
				hasBarcode(onlyAvailableItemBarcode)
			)
		));
	}

	@Test
	void shouldExcludeItemFromSameServerAsTheBorrower() {
		// Arrange
		final var bibRecordId = randomUUID();

		final var clusterRecord = clusterRecordFixture.createClusterRecord(randomUUID(), bibRecordId);

		final var sourceRecordId = "632545";

		bibRecordFixture.createBibRecord(bibRecordId,
			hostLmsFixture.findByCode(SAME_SERVER_SUPPLYING_HOST_LMS_CODE).getId(),
			sourceRecordId, clusterRecord);

		final var onlyAvailableItemId = "564325";
		final var onlyAvailableItemBarcode = "721425354";
		final var itemLocationCode = "item-location";

		sierraItemsAPIFixture.itemsForBibId(sourceRecordId, List.of(
			availableItem(onlyAvailableItemId, onlyAvailableItemBarcode,
				itemLocationCode)
		));

		agencyFixture.defineAgency(SAME_SERVER_AGENCY_CODE, SAME_SERVER_AGENCY_CODE,
			hostLmsFixture.findByCode(SAME_SERVER_SUPPLYING_HOST_LMS_CODE));

		referenceValueMappingFixture.defineLocationToAgencyMapping(SAME_SERVER_SUPPLYING_HOST_LMS_CODE,
			itemLocationCode, SAME_SERVER_AGENCY_CODE);

		final var homeLibraryCode = "home-library";

		final var patron = definePatron("356425", homeLibraryCode);

		var patronRequest = PatronRequest.builder()
			.id(randomUUID())
			.patron(patron)
			.bibClusterId(clusterRecord.getId())
			.pickupLocationCodeContext(BORROWING_HOST_LMS_CODE)
			.pickupLocationCode(PICKUP_LOCATION_CODE)
			.status(PATRON_VERIFIED)
			.patronHostlmsCode(BORROWING_HOST_LMS_CODE)
			.build();

		patronRequestsFixture.savePatronRequest(patronRequest);

		// Act
		final var resolution = resolve(patronRequest);

		// Assert
		assertThat(resolution, allOf(
			notNullValue(),
			hasNoChosenItem()
		));
	}

	@Test
	void shouldExcludeItemsFromAnAgency() {
		// Arrange
		final var bibRecordId = randomUUID();

		final var clusterRecord = clusterRecordFixture.createClusterRecord(randomUUID(), bibRecordId);

		final var sourceRecordId = "465675";

		bibRecordFixture.createBibRecord(bibRecordId, cataloguingHostLms.getId(),
			sourceRecordId, clusterRecord);

		final var onlyAvailableItemId = "651463";
		final var onlyAvailableItemBarcode = "76653672456";

		sierraItemsAPIFixture.itemsForBibId(sourceRecordId, List.of(
			availableItem(onlyAvailableItemId, onlyAvailableItemBarcode, ITEM_LOCATION_CODE)
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
			.patronHostlmsCode(BORROWING_HOST_LMS_CODE)
			.build();

		// Act
		final var resolution = resolve(patronRequest, List.of(SUPPLYING_AGENCY_CODE));

		// Assert
		assertThat(resolution, allOf(
			notNullValue(),
			hasNoChosenItem()
		));
	}

	@Test
	void shouldTolerateUnknownPatronAgency() {
		// Arrange
		final var bibRecordId = randomUUID();

		final var clusterRecord = clusterRecordFixture.createClusterRecord(randomUUID(), bibRecordId);

		final var sourceRecordId = "6736442";

		bibRecordFixture.createBibRecord(bibRecordId, cataloguingHostLms.getId(),
			sourceRecordId, clusterRecord);

		final var onlyAvailableItemId = "254535";
		final var onlyAvailableItemBarcode = "862545263";

		sierraItemsAPIFixture.itemsForBibId(sourceRecordId, List.of(
			availableItem(onlyAvailableItemId, onlyAvailableItemBarcode, ITEM_LOCATION_CODE)
		));

		final var homeLibraryCode = "home-library";

		final var patron = patronFixture.definePatron("872321", homeLibraryCode,
			cataloguingHostLms, null);

		var patronRequest = PatronRequest.builder()
			.id(randomUUID())
			.patron(patron)
			.bibClusterId(clusterRecord.getId())
			.pickupLocationCodeContext(BORROWING_HOST_LMS_CODE)
			.pickupLocationCode(PICKUP_LOCATION_CODE)
			.status(PATRON_VERIFIED)
			.patronHostlmsCode(BORROWING_HOST_LMS_CODE)
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
	void shouldKeepOrderOfAvailableItemsWhenAvailabilityDateIsTheSameDate() {
		// Arrange
		final var bibRecordId = randomUUID();

		final var clusterRecord = clusterRecordFixture.createClusterRecord(randomUUID(), bibRecordId);

		final var sourceRecordId = "465675";

		bibRecordFixture.createBibRecord(bibRecordId, cataloguingHostLms.getId(),
			sourceRecordId, clusterRecord);

		final var unavailableItemId = "372656";
		final var unavailableItemBarcode = "6256486473634";

		final var firstAvailableItemId = "651463";
		final var firstAvailableItemBarcode = "76653672456";

		final var secondAvailableItemId = "123456";
		final var secondAvailableItemBarcode = "987654321098";

		final var thirdAvailableItemId = "234567";
		final var thirdAvailableItemBarcode = "876543210987";

		sierraItemsAPIFixture.itemsForBibId(sourceRecordId, List.of(
			CheckedOutItem(unavailableItemId, unavailableItemBarcode),
			availableItem(firstAvailableItemId, firstAvailableItemBarcode, ITEM_LOCATION_CODE),
			availableItem(secondAvailableItemId, secondAvailableItemBarcode, ITEM_LOCATION_CODE),
			availableItem(thirdAvailableItemId, thirdAvailableItemBarcode, ITEM_LOCATION_CODE)
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
			.patronHostlmsCode(BORROWING_HOST_LMS_CODE)
			.build();

		patronRequestsFixture.savePatronRequest(patronRequest);

		// Act
		final var resolution = resolve(patronRequest);

		// Assert
		assertThat(resolution, allOf(
			notNullValue(),
			hasChosenItem(
				hasHostLmsCode(CIRCULATING_HOST_LMS_CODE),
				hasLocalId(firstAvailableItemId),
				hasBarcode(firstAvailableItemBarcode),
				hasLocalBibId(sourceRecordId),
				hasLocationCode(ITEM_LOCATION_CODE),
				hasAgencyCode(SUPPLYING_AGENCY_CODE)
			),
			hasAllItems(
				allOf(
					hasLocalId(unavailableItemId),
					hasBarcode(unavailableItemBarcode)
				),
				allOf(
					hasLocalId(firstAvailableItemId),
					hasBarcode(firstAvailableItemBarcode)
				),
				allOf(
					hasLocalId(secondAvailableItemId),
					hasBarcode(secondAvailableItemBarcode)
				),
				allOf(
					hasLocalId(thirdAvailableItemId),
					hasBarcode(thirdAvailableItemBarcode)
				)
			),
			hasFilteredItems(
				allOf(
					hasLocalId(firstAvailableItemId),
					hasBarcode(firstAvailableItemBarcode)
				),
				allOf(
					hasLocalId(secondAvailableItemId),
					hasBarcode(secondAvailableItemBarcode)
				),
				allOf(
					hasLocalId(thirdAvailableItemId),
					hasBarcode(thirdAvailableItemBarcode)
				)
			)
		));
	}

	@Test
	void shouldSelectAvailableItemWhenUnavailableItemsAreSelectable() {
		// Arrange
		final var bibRecordId = randomUUID();

		final var clusterRecord = clusterRecordFixture.createClusterRecord(randomUUID(), bibRecordId);

		final var sourceRecordId = "465675";

		bibRecordFixture.createBibRecord(bibRecordId, cataloguingHostLms.getId(),
			sourceRecordId, clusterRecord);

		final var checkedOutItemId = "372656";
		final var checkedOutItemBarcode = "6256486473634";

		final var availableItemId = "651463";
		final var availableItemBarcode = "76653672456";

		sierraItemsAPIFixture.itemsForBibId(sourceRecordId, List.of(
			CheckedOutItem(checkedOutItemId, checkedOutItemBarcode),
			availableItem(availableItemId, availableItemBarcode, ITEM_LOCATION_CODE)
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
			.patronHostlmsCode(BORROWING_HOST_LMS_CODE)
			.build();

		patronRequestsFixture.savePatronRequest(patronRequest);

		consortiumFixture.createConsortiumWithFunctionalSetting(FunctionalSettingType.SELECT_UNAVAILABLE_ITEMS, true);

		// Act
		final var resolution = resolve(patronRequest);

		// Assert
		assertThat(resolution, allOf(
			notNullValue(),
			hasChosenItem(
				hasHostLmsCode(CIRCULATING_HOST_LMS_CODE),
				hasLocalId(availableItemId),
				hasBarcode(availableItemBarcode),
				hasLocalBibId(sourceRecordId),
				hasLocationCode(ITEM_LOCATION_CODE),
				hasAgencyCode(SUPPLYING_AGENCY_CODE)
			)
		));
	}

	@Test
	void shouldSelectEarliestDueDateItemWhenUnavailableItemsAreSelectable() {
		// Arrange
		final var bibRecordId = randomUUID();

		final var clusterRecord = clusterRecordFixture.createClusterRecord(randomUUID(), bibRecordId);

		final var sourceRecordId = "465675";

		bibRecordFixture.createBibRecord(bibRecordId, cataloguingHostLms.getId(),
			sourceRecordId, clusterRecord);

		final var itemAId = "651463";
		final var itemABarcode = "76653672456";

		final var itemBId = "372656";
		final var itemBBarcode = "6256486473634";

		sierraItemsAPIFixture.itemsForBibId(sourceRecordId, List.of(
			CheckedOutItem(itemAId, itemABarcode, Instant.parse("2024-12-18T00:00:00Z")),
			CheckedOutItem(itemBId, itemBBarcode, Instant.parse("2024-12-01T00:00:00Z"))
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
			.patronHostlmsCode(BORROWING_HOST_LMS_CODE)
			.build();

		patronRequestsFixture.savePatronRequest(patronRequest);

		consortiumFixture.createConsortiumWithFunctionalSetting(FunctionalSettingType.SELECT_UNAVAILABLE_ITEMS, true);

		// Act
		final var resolution = resolve(patronRequest);

		// Assert
		assertThat(resolution, allOf(
			notNullValue(),
			hasChosenItem(
				hasHostLmsCode(CIRCULATING_HOST_LMS_CODE),
				hasLocalId(itemBId),
				hasBarcode(itemBBarcode),
				hasLocalBibId(sourceRecordId),
				hasLocationCode(ITEM_LOCATION_CODE),
				hasAgencyCode(SUPPLYING_AGENCY_CODE)
			)
		));
	}

	@Test
	void shouldFallBackToExistingResolutionWhenNoCheckedOutItemsAvailable() {
		// Arrange
		final var bibRecordId = randomUUID();

		final var clusterRecord = clusterRecordFixture.createClusterRecord(randomUUID(), bibRecordId);

		final var sourceRecordId = "465675";

		bibRecordFixture.createBibRecord(bibRecordId, cataloguingHostLms.getId(),
			sourceRecordId, clusterRecord);

		sierraItemsAPIFixture.itemsForBibId(sourceRecordId, List.of());

		final var homeLibraryCode = "home-library";

		final var patron = definePatron("872321", homeLibraryCode);

		var patronRequest = PatronRequest.builder()
			.id(randomUUID())
			.patron(patron)
			.bibClusterId(clusterRecord.getId())
			.pickupLocationCodeContext(BORROWING_HOST_LMS_CODE)
			.pickupLocationCode(PICKUP_LOCATION_CODE)
			.status(PATRON_VERIFIED)
			.patronHostlmsCode(BORROWING_HOST_LMS_CODE)
			.build();

		patronRequestsFixture.savePatronRequest(patronRequest);

		consortiumFixture.createConsortiumWithFunctionalSetting(FunctionalSettingType.SELECT_UNAVAILABLE_ITEMS, true);

		// Act
		final var resolution = resolve(patronRequest);

		// Assert
		assertThat(resolution, allOf(
			notNullValue(),
			hasNoChosenItem()
		));
	}

	@Test
	void shouldIncludeItemsWithHoldsWhenSelectUnavailableItemsIsOn() {
		// Arrange
		final var bibRecordId = randomUUID();

		final var clusterRecord = clusterRecordFixture.createClusterRecord(randomUUID(), bibRecordId);

		final var sourceRecordId = "465675";

		bibRecordFixture.createBibRecord(bibRecordId, cataloguingHostLms.getId(),
			sourceRecordId, clusterRecord);

		final var itemWithHoldsId = "372656";
		final var itemWithHoldsBarcode = "6256486473634";

		final var availableItemId = "651463";
		final var availableItemBarcode = "76653672456";

		final var checkedOutItemId = "372656";
		final var checkedOutItemBarcode = "6256486473634";

		sierraItemsAPIFixture.itemsForBibId(sourceRecordId, List.of(
			itemWithHolds(itemWithHoldsId, itemWithHoldsBarcode, 1),
			availableItem(availableItemId, availableItemBarcode, ITEM_LOCATION_CODE),
			CheckedOutItem(checkedOutItemId, checkedOutItemBarcode)
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
			.patronHostlmsCode(BORROWING_HOST_LMS_CODE)
			.build();

		patronRequestsFixture.savePatronRequest(patronRequest);

		consortiumFixture.createConsortiumWithFunctionalSetting(FunctionalSettingType.SELECT_UNAVAILABLE_ITEMS, true);

		// Act
		final var resolution = resolve(patronRequest);

		// Assert
		assertThat(resolution, allOf(
			notNullValue(),
			hasChosenItem(
				hasLocalId(itemWithHoldsId),
				hasBarcode(itemWithHoldsBarcode)
			),
			hasFilteredItemsSize(3),
			hasFilteredItems(
				allOf(
					hasLocalId(itemWithHoldsId),
					hasBarcode(itemWithHoldsBarcode)
				),
				allOf(
					hasLocalId(availableItemId),
					hasBarcode(availableItemBarcode)
				),
				allOf(
					hasLocalId(checkedOutItemId),
					hasBarcode(checkedOutItemBarcode)
				)
			)
		));
	}

	@Test
	void shouldExcludeItemsWithHoldsWhenSelectUnavailableItemsIsNotDefined() {
		// Arrange
		final var bibRecordId = randomUUID();

		final var clusterRecord = clusterRecordFixture.createClusterRecord(randomUUID(), bibRecordId);

		final var sourceRecordId = "465675";

		bibRecordFixture.createBibRecord(bibRecordId, cataloguingHostLms.getId(),
			sourceRecordId, clusterRecord);

		final var itemWithHoldsId = "372656";
		final var itemWithHoldsBarcode = "6256486473634";

		final var availableItemId = "651463";
		final var availableItemBarcode = "76653672456";

		final var checkedOutItemId = "372656";
		final var checkedOutItemBarcode = "6256486473634";

		sierraItemsAPIFixture.itemsForBibId(sourceRecordId, List.of(
			itemWithHolds(itemWithHoldsId, itemWithHoldsBarcode, 1),
			availableItem(availableItemId, availableItemBarcode, ITEM_LOCATION_CODE),
			CheckedOutItem(checkedOutItemId, checkedOutItemBarcode)
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
			.patronHostlmsCode(BORROWING_HOST_LMS_CODE)
			.build();

		patronRequestsFixture.savePatronRequest(patronRequest);

		// Act
		final var resolution = resolve(patronRequest);

		// Assert
		assertThat(resolution, allOf(
			notNullValue(),
			hasChosenItem(
				hasLocalId(availableItemId),
				hasBarcode(availableItemBarcode)
			),
			hasFilteredItemsSize(1),
			hasFilteredItems(
				allOf(
					hasLocalId(availableItemId),
					hasBarcode(availableItemBarcode)
				)
			)
		));
	}

	@Test
	void shouldExcludeItemsWithHoldsWhenSelectUnavailableItemsIsOff() {
		// Arrange
		final var bibRecordId = randomUUID();

		final var clusterRecord = clusterRecordFixture.createClusterRecord(randomUUID(), bibRecordId);

		final var sourceRecordId = "465675";

		bibRecordFixture.createBibRecord(bibRecordId, cataloguingHostLms.getId(),
			sourceRecordId, clusterRecord);

		final var itemWithHoldsId = "372656";
		final var itemWithHoldsBarcode = "6256486473634";

		final var availableItemId = "651463";
		final var availableItemBarcode = "76653672456";

		final var checkedOutItemId = "372656";
		final var checkedOutItemBarcode = "6256486473634";

		sierraItemsAPIFixture.itemsForBibId(sourceRecordId, List.of(
			itemWithHolds(itemWithHoldsId, itemWithHoldsBarcode, 1),
			availableItem(availableItemId, availableItemBarcode, ITEM_LOCATION_CODE),
			CheckedOutItem(checkedOutItemId, checkedOutItemBarcode)
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
			.patronHostlmsCode(BORROWING_HOST_LMS_CODE)
			.build();

		patronRequestsFixture.savePatronRequest(patronRequest);

		consortiumFixture.createConsortiumWithFunctionalSetting(FunctionalSettingType.SELECT_UNAVAILABLE_ITEMS, false);

		// Act
		final var resolution = resolve(patronRequest);

		// Assert
		assertThat(resolution, allOf(
			notNullValue(),
			hasChosenItem(
				hasLocalId(availableItemId),
				hasBarcode(availableItemBarcode)
			),
			hasFilteredItemsSize(1),
			hasFilteredItems(
				allOf(
					hasLocalId(availableItemId),
					hasBarcode(availableItemBarcode)
				)
			)
		));
	}

	@Test
	void shouldNotSelectItemWithHoldsWhenStatusNotKnownAndSelectUnavailableItemsIsOn() {
		// Arrange
		final var bibRecordId = randomUUID();
		final var clusterRecord = clusterRecordFixture.createClusterRecord(randomUUID(), bibRecordId);
		final var sourceRecordId = "465675";
		bibRecordFixture.createBibRecord(bibRecordId, cataloguingHostLms.getId(),
			sourceRecordId, clusterRecord);

		final var unavailableItemWithHoldsId = "372656";
		final var unavailableItemWithHoldsBarcode = "6256486473634";

		sierraItemsAPIFixture.itemsForBibId(sourceRecordId, List.of(
			unavailableItemWithHolds(unavailableItemWithHoldsId, unavailableItemWithHoldsBarcode)
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
			.patronHostlmsCode(BORROWING_HOST_LMS_CODE)
			.build();

		patronRequestsFixture.savePatronRequest(patronRequest);

		consortiumFixture.createConsortiumWithFunctionalSetting(FunctionalSettingType.SELECT_UNAVAILABLE_ITEMS, true);

		// Act
		final var resolution = resolve(patronRequest);

		// Assert
		assertThat(resolution, allOf(
			notNullValue(),
			hasNoChosenItem(),
			hasFilteredItemsSize(0)
		));
	}

	@Test
	void shouldSelectItemsNormallyWhenSelectUnavailableItemsIsOnButNoItemsWithHolds() {
		// Arrange
		final var bibRecordId = randomUUID();

		final var clusterRecord = clusterRecordFixture.createClusterRecord(randomUUID(), bibRecordId);

		final var sourceRecordId = "465675";

		bibRecordFixture.createBibRecord(bibRecordId, cataloguingHostLms.getId(),
			sourceRecordId, clusterRecord);

		final var availableItemId = "651463";
		final var availableItemBarcode = "76653672456";

		sierraItemsAPIFixture.itemsForBibId(sourceRecordId, List.of(
			availableItem(availableItemId, availableItemBarcode, ITEM_LOCATION_CODE)
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
			.patronHostlmsCode(BORROWING_HOST_LMS_CODE)
			.build();

		patronRequestsFixture.savePatronRequest(patronRequest);

		consortiumFixture.createConsortiumWithFunctionalSetting(FunctionalSettingType.SELECT_UNAVAILABLE_ITEMS, true);

		// Act
		final var resolution = resolve(patronRequest);

		// Assert
		assertThat(resolution, allOf(
			notNullValue(),
			hasChosenItem(
				hasHostLmsCode(CIRCULATING_HOST_LMS_CODE),
				hasLocalId(availableItemId),
				hasBarcode(availableItemBarcode)
			)
		));
	}

	private Resolution resolve(PatronRequest patronRequest) {
		return resolve(patronRequest, emptyList());
	}

	private Resolution resolve(PatronRequest patronRequest, List<String> excludedAgencyCodes) {
		return singleValueFrom(patronRequestResolutionService
			.resolvePatronRequest(patronRequest, excludedAgencyCodes));
	}

	private Patron definePatron(String localId, String homeLibraryCode) {
		return patronFixture.definePatron(localId, homeLibraryCode,
			cataloguingHostLms, agencyFixture.findByCode(BORROWING_AGENCY_CODE));
	}

	private SierraItem availableItem(String id, String barcode,
		 String itemLocationCode) {

		return SierraItem.builder()
			.id(id)
			.barcode(barcode)
			.locationCode(itemLocationCode)
			.statusCode("-")
			// needs to align with NumericRangeMapping
			.itemType("1")
			.fixedFields(Map.of(61, FixedField.builder().value("1").build()))
			.build();
	}

	private SierraItem CheckedOutItem(String id, String barcode) {
		return SierraItem.builder()
			.id(id)
			.barcode(barcode)
			.locationCode(ITEM_LOCATION_CODE)
			.statusCode("-")
			// Sierra item with due date is considered not available
			.dueDate(Instant.now().plus(3, HOURS))
			// needs to align with NumericRangeMapping
			.itemType("1")
			.fixedFields(Map.of(61, FixedField.builder().value("1").build()))
			.build();
	}

	// Helper method to create a checked out item with a specific due date
	private SierraItem CheckedOutItem(String id, String barcode, Instant dueDate) {
		return SierraItem.builder()
			.id(id)
			.barcode(barcode)
			.locationCode(ITEM_LOCATION_CODE)
			.statusCode("-")
			.dueDate(dueDate)
			.itemType("1")
			.fixedFields(Map.of(61, FixedField.builder().value("1").build()))
			.build();
	}

	private SierraItem itemWithHolds(String itemWithHoldsId, String itemWithHoldsBarcode, int holdCount) {
		return SierraItem.builder()
			.id(itemWithHoldsId)
			.barcode(itemWithHoldsBarcode)
			.locationCode(ITEM_LOCATION_CODE)
			.statusCode("-")
			.holdCount(holdCount)
			.itemType("1")
			.fixedFields(Map.of(61, FixedField.builder().value("1").build()))
			.build();
	}

	private SierraItem unavailableItemWithHolds(String itemWithHoldsId, String itemWithHoldsBarcode) {
		return SierraItem.builder()
			.id(itemWithHoldsId)
			.barcode(itemWithHoldsBarcode)
			.locationCode(ITEM_LOCATION_CODE)
			.statusCode("#")
			.holdCount(1)
			.itemType("1")
			.fixedFields(Map.of(61, FixedField.builder().value("1").build()))
			.build();
	}

	@SafeVarargs
	private Matcher<Resolution> hasChosenItem(Matcher<Item>... matchers) {
		return hasProperty("chosenItem", allOf(matchers));
	}

	private Matcher<Resolution> hasNoChosenItem() {
		return hasProperty("chosenItem", is(nullValue()));
	}

	@SafeVarargs
	private static Matcher<Resolution> hasAllItems(Matcher<Item>... matchers) {
		return hasProperty("allItems", contains(matchers));
	}

	@SafeVarargs
	private static Matcher<Resolution> hasFilteredItems(Matcher<Item>... matchers) {
		return hasProperty("filteredItems", contains(matchers));
	}

	private static Matcher<Resolution> hasFilteredItemsSize(int size) {
		return hasProperty("filteredItems", hasSize(size));
	}
}
