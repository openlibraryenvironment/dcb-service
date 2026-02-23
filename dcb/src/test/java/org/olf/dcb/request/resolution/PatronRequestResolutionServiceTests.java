package org.olf.dcb.request.resolution;

import static java.time.temporal.ChronoUnit.HOURS;
import static java.util.Collections.emptyList;
import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.olf.dcb.core.model.FunctionalSettingType.OWN_LIBRARY_BORROWING;
import static org.olf.dcb.core.model.FunctionalSettingType.SELECT_UNAVAILABLE_ITEMS;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;
import static org.olf.dcb.test.matchers.ItemMatchers.hasAgencyCode;
import static org.olf.dcb.test.matchers.ItemMatchers.hasBarcode;
import static org.olf.dcb.test.matchers.ItemMatchers.hasHostLmsCode;
import static org.olf.dcb.test.matchers.ItemMatchers.hasLocalBibId;
import static org.olf.dcb.test.matchers.ItemMatchers.hasLocalId;
import static org.olf.dcb.test.matchers.ItemMatchers.hasLocationCode;
import static org.olf.dcb.test.matchers.ResolutionMatchers.hasAllItems;
import static org.olf.dcb.test.matchers.ResolutionMatchers.hasChosenItem;
import static org.olf.dcb.test.matchers.ResolutionMatchers.hasFilteredItems;
import static org.olf.dcb.test.matchers.ResolutionMatchers.hasFilteredItemsSize;
import static org.olf.dcb.test.matchers.ResolutionMatchers.hasNoChosenItem;
import static org.olf.dcb.test.matchers.ThrowableMatchers.hasMessage;
import static org.olf.dcb.utils.PropertyAccessUtils.getValueOrNull;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockserver.client.MockServerClient;
import org.olf.dcb.core.clustering.model.ClusterRecord;
import org.olf.dcb.core.interaction.shared.MissingParameterException;
import org.olf.dcb.core.interaction.sierra.SierraApiFixtureProvider;
import org.olf.dcb.core.interaction.sierra.SierraItem;
import org.olf.dcb.core.interaction.sierra.SierraItemsAPIFixture;
import org.olf.dcb.core.model.DataAgency;
import org.olf.dcb.core.model.DataHostLms;
import org.olf.dcb.core.model.FunctionalSettingType;
import org.olf.dcb.core.model.Location;
import org.olf.dcb.test.AgencyFixture;
import org.olf.dcb.test.BibRecordFixture;
import org.olf.dcb.test.ClusterRecordFixture;
import org.olf.dcb.test.ConsortiumFixture;
import org.olf.dcb.test.HostLmsFixture;
import org.olf.dcb.test.LocationFixture;
import org.olf.dcb.test.ReferenceValueMappingFixture;

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
	private ReferenceValueMappingFixture referenceValueMappingFixture;
	@Inject
	private AgencyFixture agencyFixture;
	@Inject
	private LocationFixture locationFixture;
	@Inject
	private ConsortiumFixture consortiumFixture;

	private SierraItemsAPIFixture sierraItemsAPIFixture;

	private DataHostLms cataloguingHostLms;

	private String borrowingAgencyPickupLocationId;
	private DataAgency borrowingAgency;

	@BeforeAll
	@SneakyThrows
	public void beforeAll(MockServerClient mockServerClient) {
		final var host = "resolution-service-tests.com";
		final var baseUrl = "https://" + host;
		final var token = "resolution-system-token";
		final var key = "resolution-system-key";
		final var secret = "resolution-system-secret";

		SierraTestUtils.mockFor(mockServerClient, baseUrl)
			.setValidCredentials(key, secret, token, 60);

		sierraItemsAPIFixture = sierraApiFixtureProvider.items(mockServerClient, host);

		hostLmsFixture.deleteAll();

		hostLmsFixture.createSierraHostLms(CATALOGUING_HOST_LMS_CODE, key,
			secret, baseUrl, "item");

		cataloguingHostLms = hostLmsFixture.findByCode(CATALOGUING_HOST_LMS_CODE);

		hostLmsFixture.createSierraHostLms(CIRCULATING_HOST_LMS_CODE, "",
			"", "http://some-system", "item");

		hostLmsFixture.createSierraHostLms(BORROWING_HOST_LMS_CODE, key,
			secret, baseUrl, "item");

		// create a supplying host LMS that is on the same server as the borrower
		hostLmsFixture.createSierraHostLms(SAME_SERVER_SUPPLYING_HOST_LMS_CODE, key,
			secret, baseUrl, "item");
	}

	@BeforeEach
	void beforeEach() {
		clusterRecordFixture.deleteAll();
		bibRecordFixture.deleteAll();
		referenceValueMappingFixture.deleteAll();
		agencyFixture.deleteAll();
		locationFixture.deleteAll();
		consortiumFixture.deleteAll();

		referenceValueMappingFixture.defineLocationToAgencyMapping(
			CATALOGUING_HOST_LMS_CODE, ITEM_LOCATION_CODE, SUPPLYING_AGENCY_CODE);

		agencyFixture.defineAgency(SUPPLYING_AGENCY_CODE, SUPPLYING_AGENCY_CODE,
			hostLmsFixture.findByCode(CIRCULATING_HOST_LMS_CODE));

		borrowingAgency = agencyFixture.defineAgency(BORROWING_AGENCY_CODE,
			BORROWING_AGENCY_CODE, hostLmsFixture.findByCode(BORROWING_HOST_LMS_CODE));

		final var borrowingAgencyPickupLocation = locationFixture.createPickupLocation(
			borrowingAgency);

		borrowingAgencyPickupLocationId = getValueOrNull(borrowingAgencyPickupLocation,
			Location::getId, UUID::toString);

		// Needs to align with sierra item type responses
		// For simplicity all sierra item types are expected to be on 1 in this class
		referenceValueMappingFixture.defineLocalToCanonicalItemTypeRangeMapping(
			cataloguingHostLms.getCode(), 1, 1, "loanable-item");
	}

	@Test
	void shouldChooseFirstAvailableItem() {
		// Arrange
		final var bibRecordId = randomUUID();

		final var clusterRecord = createClusterRecord(bibRecordId);

		final var sourceRecordId = "465675";

		bibRecordFixture.createBibRecord(bibRecordId, cataloguingHostLms.getId(),
			sourceRecordId, clusterRecord);

		final var onlyAvailableItemId = "651463";
		final var onlyAvailableItemBarcode = "76653672456";

		final var unavailableItemId = "372656";
		final var unavailableItemBarcode = "6256486473634";

		sierraItemsAPIFixture.itemsForBibId(sourceRecordId, List.of(
			checkedOutItem(unavailableItemId, unavailableItemBarcode),
			availableItem(onlyAvailableItemId, onlyAvailableItemBarcode, ITEM_LOCATION_CODE)
		));

		// Act
		final var parameters = standardParametersFor(clusterRecord);

		final var resolution = resolve(parameters);

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

		final var clusterRecord = createClusterRecord(bibRecordId);

		final var sourceRecordId = "174625";

		bibRecordFixture.createBibRecord(bibRecordId, cataloguingHostLms.getId(),
			sourceRecordId, clusterRecord);

		final var onlyAvailableItemId = "826425";
		final var onlyAvailableItemBarcode = "25452553";

		sierraItemsAPIFixture.itemsForBibId(sourceRecordId, List.of(
			availableItem(onlyAvailableItemId, onlyAvailableItemBarcode,
				ITEM_LOCATION_CODE)
		));

		// Act
		final var parameters = ResolutionParameters.builder()
			.borrowingAgencyCode(BORROWING_AGENCY_CODE)
			.borrowingHostLmsCode(BORROWING_HOST_LMS_CODE)
			.bibClusterId(getValueOrNull(clusterRecord, ClusterRecord::getId))
			.pickupLocationCode(borrowingAgencyPickupLocationId)
			.pickupAgencyCode(getValueOrNull(borrowingAgency, DataAgency::getCode))
			.excludedSupplyingAgencyCodes(emptyList())
			.manualItemSelection(ManualItemSelection.builder()
				.isManuallySelected(true)
				.localItemId(onlyAvailableItemId)
				.hostLmsCode(CIRCULATING_HOST_LMS_CODE)
				.agencyCode(SUPPLYING_AGENCY_CODE)
				.build())
			.build();

		final var resolution = resolve(parameters);

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
	void shouldNotSelectsUnavailableManuallySelectedItem() {
		// Arrange
		disallowUnavailableItems();

		final var bibRecordId = randomUUID();

		final var clusterRecord = createClusterRecord(bibRecordId);

		final var sourceRecordId = "462655";

		bibRecordFixture.createBibRecord(bibRecordId, cataloguingHostLms.getId(),
			sourceRecordId, clusterRecord);

		final var unavailableItemId = "7685845";
		final var unavailableItemBarcode = "2645535";

		sierraItemsAPIFixture.itemsForBibId(sourceRecordId, List.of(
			checkedOutItem(unavailableItemId, unavailableItemBarcode),
			// Any other item
			availableItem("683855", "694828563", ITEM_LOCATION_CODE)
		));

		// Act
		final var parameters = ResolutionParameters.builder()
			.borrowingAgencyCode(BORROWING_AGENCY_CODE)
			.borrowingHostLmsCode(BORROWING_HOST_LMS_CODE)
			.bibClusterId(getValueOrNull(clusterRecord, ClusterRecord::getId))
			.pickupLocationCode(borrowingAgencyPickupLocationId)
			.pickupAgencyCode(BORROWING_AGENCY_CODE)
			.excludedSupplyingAgencyCodes(emptyList())
			.manualItemSelection(ManualItemSelection.builder()
				.isManuallySelected(true)
				.localItemId(unavailableItemId)
				.hostLmsCode(CIRCULATING_HOST_LMS_CODE)
				.agencyCode(SUPPLYING_AGENCY_CODE)
				.build())
			.build();

		final var resolution = resolve(parameters);

		// Assert
		assertThat(resolution, allOf(
			notNullValue(),
			hasNoChosenItem()
		));
	}

	@Test
	void shouldNotSelectsUnfoundManuallySelectedItem() {
		// Arrange
		final var bibRecordId = randomUUID();

		final var clusterRecord = createClusterRecord(bibRecordId);

		final var sourceRecordId = "826559";

		bibRecordFixture.createBibRecord(bibRecordId, cataloguingHostLms.getId(),
			sourceRecordId, clusterRecord);

		final var notFoundItemId = "7274653";

		sierraItemsAPIFixture.itemsForBibId(sourceRecordId, emptyList());

		// Act
		final var parameters = ResolutionParameters.builder()
			.borrowingAgencyCode(BORROWING_AGENCY_CODE)
			.borrowingHostLmsCode(BORROWING_HOST_LMS_CODE)
			.bibClusterId(getValueOrNull(clusterRecord, ClusterRecord::getId))
			.pickupLocationCode(borrowingAgencyPickupLocationId)
			.pickupAgencyCode(BORROWING_AGENCY_CODE)
			.excludedSupplyingAgencyCodes(emptyList())
			.manualItemSelection(ManualItemSelection.builder()
				.isManuallySelected(true)
				.localItemId(notFoundItemId)
				.hostLmsCode(CIRCULATING_HOST_LMS_CODE)
				.agencyCode(SUPPLYING_AGENCY_CODE)
				.build())
			.build();

		final var resolution = resolve(parameters);

		// Assert
		assertThat(resolution, allOf(
			notNullValue(),
			hasNoChosenItem()
		));
	}

	@Test
	void shouldExcludeUnavailableItemWhenSettingDisabled() {
		// Arrange
		disallowUnavailableItems();

		final var bibRecordId = randomUUID();

		final var clusterRecord = createClusterRecord(bibRecordId);

		final var sourceRecordId = "2656846";

		bibRecordFixture.createBibRecord(bibRecordId, cataloguingHostLms.getId(),
			sourceRecordId, clusterRecord);

		final var unavailableItemId = "372656";
		final var unavailableItemBarcode = "6256486473634";

		sierraItemsAPIFixture.itemsForBibId(sourceRecordId, List.of(
			checkedOutItem(unavailableItemId, unavailableItemBarcode)
		));

		// Act
		final var parameters = standardParametersFor(clusterRecord);

		final var resolution = resolve(parameters);

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

		final var clusterRecord = createClusterRecord(bibRecordId);

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

		// Act
		final var parameters = standardParametersFor(clusterRecord);

		final var resolution = resolve(parameters);

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

		final var clusterRecord = createClusterRecord(bibRecordId);

		final var sourceRecordId = "465675";

		bibRecordFixture.createBibRecord(bibRecordId, cataloguingHostLms.getId(),
			sourceRecordId, clusterRecord);

		final var onlyAvailableItemId = "651463";
		final var onlyAvailableItemBarcode = "76653672456";

		final var unavailableItemId = "372656";
		final var unavailableItemBarcode = "6256486473634";

		sierraItemsAPIFixture.itemsForBibId(sourceRecordId, List.of(
			checkedOutItem(unavailableItemId, unavailableItemBarcode),
			availableItem(onlyAvailableItemId, onlyAvailableItemBarcode, ITEM_LOCATION_CODE)
		), 2000);

		// Act
		final var parameters = standardParametersFor(clusterRecord);

		final var resolution = resolve(parameters);

		// Assert
		assertThat(resolution, allOf(
			notNullValue(),
			hasNoChosenItem()
		));
	}

	@Test
	void shouldExcludeItemFromSameAgencyAsBorrowerWhenSettingIsDisabledForConsortia() {
		// Arrange
		defineSetting(OWN_LIBRARY_BORROWING, false);

		final var bibRecordId = randomUUID();

		final var clusterRecord = createClusterRecord(bibRecordId);

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

		// Act
		final var parameters = standardParametersFor(clusterRecord);

		final var resolution = resolve(parameters);

		// Assert
		assertThat(resolution, allOf(
			notNullValue(),
			hasNoChosenItem()
		));
	}

	@Test
	void shouldIncludeItemFromSameAgencyAsBorrowerWhenSettingIsEnabledForConsortia() {
		// Arrange
		defineSetting(OWN_LIBRARY_BORROWING, true);

		final var bibRecordId = randomUUID();

		final var clusterRecord = createClusterRecord(bibRecordId);

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

		// Act
		final var parameters = standardParametersFor(clusterRecord);

		final var resolution = resolve(parameters);

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

		final var clusterRecord = createClusterRecord(bibRecordId);

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

		String sameServerAgencyCode = "same-server-agency";

		agencyFixture.defineAgency(sameServerAgencyCode, sameServerAgencyCode,
			hostLmsFixture.findByCode(SAME_SERVER_SUPPLYING_HOST_LMS_CODE));

		referenceValueMappingFixture.defineLocationToAgencyMapping(SAME_SERVER_SUPPLYING_HOST_LMS_CODE,
			itemLocationCode, sameServerAgencyCode);

		// Act
		final var parameters = standardParametersFor(clusterRecord);

		final var resolution = resolve(parameters);

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

		final var clusterRecord = createClusterRecord(bibRecordId);

		final var sourceRecordId = "465675";

		bibRecordFixture.createBibRecord(bibRecordId, cataloguingHostLms.getId(),
			sourceRecordId, clusterRecord);

		final var onlyAvailableItemId = "651463";
		final var onlyAvailableItemBarcode = "76653672456";

		sierraItemsAPIFixture.itemsForBibId(sourceRecordId, List.of(
			availableItem(onlyAvailableItemId, onlyAvailableItemBarcode, ITEM_LOCATION_CODE)
		));

		// Act
		final var parameters = ResolutionParameters.builder()
			.borrowingAgencyCode(BORROWING_AGENCY_CODE)
			.borrowingHostLmsCode(BORROWING_HOST_LMS_CODE)
			.bibClusterId(getValueOrNull(clusterRecord, ClusterRecord::getId))
			.pickupLocationCode(borrowingAgencyPickupLocationId)
			.pickupAgencyCode(BORROWING_AGENCY_CODE)
			.excludedSupplyingAgencyCodes(List.of(SUPPLYING_AGENCY_CODE))
			.build();

		final var resolution = resolve(parameters);

		// Assert
		assertThat(resolution, allOf(
			notNullValue(),
			hasNoChosenItem()
		));
	}

	@Test
	void shouldNotTolerateUnknownBorrowingAgency() {
		// Arrange
		final var bibRecordId = randomUUID();

		final var clusterRecord = createClusterRecord(bibRecordId);

		final var sourceRecordId = "6736442";

		bibRecordFixture.createBibRecord(bibRecordId, cataloguingHostLms.getId(),
			sourceRecordId, clusterRecord);

		final var onlyAvailableItemId = "254535";
		final var onlyAvailableItemBarcode = "862545263";

		sierraItemsAPIFixture.itemsForBibId(sourceRecordId, List.of(
			availableItem(onlyAvailableItemId, onlyAvailableItemBarcode, ITEM_LOCATION_CODE)
		));

		// Act
		final var parameters = ResolutionParameters.builder()
			.borrowingAgencyCode(null)
			.borrowingHostLmsCode(BORROWING_HOST_LMS_CODE)
			.bibClusterId(getValueOrNull(clusterRecord, ClusterRecord::getId))
			.pickupLocationCode(borrowingAgencyPickupLocationId)
			.pickupAgencyCode(null)
			.excludedSupplyingAgencyCodes(emptyList())
			.build();

		final var error = assertThrows(MissingParameterException.class,
			() -> resolve(parameters));

		// Assert
		assertThat(error, allOf(
			notNullValue(),
			hasMessage("borrowingAgencyCode is missing.")
		));
	}

	@Test
	void shouldKeepOrderOfAvailableItemsWhenAvailabilityDateIsTheSameDate() {
		// Arrange
		final var bibRecordId = randomUUID();

		final var clusterRecord = createClusterRecord(bibRecordId);

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
			checkedOutItem(unavailableItemId, unavailableItemBarcode),
			availableItem(firstAvailableItemId, firstAvailableItemBarcode, ITEM_LOCATION_CODE),
			availableItem(secondAvailableItemId, secondAvailableItemBarcode, ITEM_LOCATION_CODE),
			availableItem(thirdAvailableItemId, thirdAvailableItemBarcode, ITEM_LOCATION_CODE)
		));

		// Act
		final var parameters = standardParametersFor(clusterRecord);

		final var resolution = resolve(parameters);

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

		final var clusterRecord = createClusterRecord(bibRecordId);

		final var sourceRecordId = "465675";

		bibRecordFixture.createBibRecord(bibRecordId, cataloguingHostLms.getId(),
			sourceRecordId, clusterRecord);

		final var checkedOutItemId = "372656";
		final var checkedOutItemBarcode = "6256486473634";

		final var availableItemId = "651463";
		final var availableItemBarcode = "76653672456";

		sierraItemsAPIFixture.itemsForBibId(sourceRecordId, List.of(
			checkedOutItem(checkedOutItemId, checkedOutItemBarcode),
			availableItem(availableItemId, availableItemBarcode, ITEM_LOCATION_CODE)
		));

		allowUnavailableItems();

		// Act
		final var parameters = standardParametersFor(clusterRecord);

		final var resolution = resolve(parameters);

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

		final var clusterRecord = createClusterRecord(bibRecordId);

		final var sourceRecordId = "465675";

		bibRecordFixture.createBibRecord(bibRecordId, cataloguingHostLms.getId(),
			sourceRecordId, clusterRecord);

		final var itemAId = "651463";
		final var itemABarcode = "76653672456";

		final var itemBId = "372656";
		final var itemBBarcode = "6256486473634";

		sierraItemsAPIFixture.itemsForBibId(sourceRecordId, List.of(
			checkedOutItem(itemAId, itemABarcode,Instant.now().plus(84, ChronoUnit.DAYS)), // CHECKED_OUT item due to be returned 84 days from now (3 DEFAULT_LOAN_PERIODS)
			checkedOutItem(itemBId, itemBBarcode, Instant.parse("2024-12-01T00:00:00Z")) // Overdue item with 0 hold count. DEFAULT_LOAN_PERIOD (28 days) will be applied for availability date
		));

		allowUnavailableItems();

		// Act
		final var parameters = standardParametersFor(clusterRecord);

		final var resolution = resolve(parameters);

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

		final var clusterRecord = createClusterRecord(bibRecordId);

		final var sourceRecordId = "465675";

		bibRecordFixture.createBibRecord(bibRecordId, cataloguingHostLms.getId(),
			sourceRecordId, clusterRecord);

		sierraItemsAPIFixture.itemsForBibId(sourceRecordId, List.of());

		allowUnavailableItems();

		// Act
		final var parameters = standardParametersFor(clusterRecord);

		final var resolution = resolve(parameters);

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

		final var clusterRecord = createClusterRecord(bibRecordId);

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
			itemWithHolds(itemWithHoldsId, itemWithHoldsBarcode),
			availableItem(availableItemId, availableItemBarcode, ITEM_LOCATION_CODE),
			checkedOutItem(checkedOutItemId, checkedOutItemBarcode)
		));

		allowUnavailableItems();

		// Act
		final var parameters = standardParametersFor(clusterRecord);

		final var resolution = resolve(parameters);

		// Assert
		assertThat(resolution, allOf(
			notNullValue(),
			hasChosenItem(
				hasLocalId(availableItemId),
				hasBarcode(availableItemBarcode)
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

		final var clusterRecord = createClusterRecord(bibRecordId);

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
			itemWithHolds(itemWithHoldsId, itemWithHoldsBarcode),
			availableItem(availableItemId, availableItemBarcode, ITEM_LOCATION_CODE),
			checkedOutItem(checkedOutItemId, checkedOutItemBarcode)
		));

		// Act
		final var parameters = standardParametersFor(clusterRecord);

		final var resolution = resolve(parameters);

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

		final var clusterRecord = createClusterRecord(bibRecordId);

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
			itemWithHolds(itemWithHoldsId, itemWithHoldsBarcode),
			availableItem(availableItemId, availableItemBarcode, ITEM_LOCATION_CODE),
			checkedOutItem(checkedOutItemId, checkedOutItemBarcode)
		));

		disallowUnavailableItems();

		// Act
		final var parameters = standardParametersFor(clusterRecord);

		final var resolution = resolve(parameters);

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
		final var clusterRecord = createClusterRecord(bibRecordId);
		final var sourceRecordId = "465675";
		bibRecordFixture.createBibRecord(bibRecordId, cataloguingHostLms.getId(),
			sourceRecordId, clusterRecord);

		final var unavailableItemWithHoldsId = "372656";
		final var unavailableItemWithHoldsBarcode = "6256486473634";

		sierraItemsAPIFixture.itemsForBibId(sourceRecordId, List.of(
			unavailableItemWithHolds(unavailableItemWithHoldsId, unavailableItemWithHoldsBarcode)
		));

		allowUnavailableItems();

		// Act
		final var parameters = standardParametersFor(clusterRecord);

		final var resolution = resolve(parameters);

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

		final var clusterRecord = createClusterRecord(bibRecordId);

		final var sourceRecordId = "465675";

		bibRecordFixture.createBibRecord(bibRecordId, cataloguingHostLms.getId(),
			sourceRecordId, clusterRecord);

		final var availableItemId = "651463";
		final var availableItemBarcode = "76653672456";

		sierraItemsAPIFixture.itemsForBibId(sourceRecordId, List.of(
			availableItem(availableItemId, availableItemBarcode, ITEM_LOCATION_CODE)
		));

		allowUnavailableItems();

		// Act
		final var parameters = standardParametersFor(clusterRecord);

		final var resolution = resolve(parameters);

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

	@Test
	void shouldNotSelectItemFromBorrowingLibraryWhenPickupIsElsewhere() {
		// Arrange

		// Allow own library borrowing (otherwise will falsely exclude items anyway)
		allowOwnLibraryBorrowing();

		final var bibRecordId = randomUUID();

		final var clusterRecord = createClusterRecord(bibRecordId);

		final var sourceRecordId = "264895";

		bibRecordFixture.createBibRecord(bibRecordId,
			hostLmsFixture.findByCode(BORROWING_HOST_LMS_CODE).getId(),
			sourceRecordId, clusterRecord);

		final var availableItemId = "362557";
		final var availableItemBarcode = "862456475";

		final var atBorrowingLibraryLocation = "borrowing-library-item-location";

		referenceValueMappingFixture.defineLocationToAgencyMapping(
			BORROWING_HOST_LMS_CODE, atBorrowingLibraryLocation, BORROWING_AGENCY_CODE);

		sierraItemsAPIFixture.itemsForBibId(sourceRecordId, List.of(
			availableItem(availableItemId, availableItemBarcode, atBorrowingLibraryLocation)
		));

		// Act
		final var parameters = ResolutionParameters.builder()
			.borrowingAgencyCode(BORROWING_AGENCY_CODE)
			.borrowingHostLmsCode(BORROWING_HOST_LMS_CODE)
			.bibClusterId(getValueOrNull(clusterRecord, ClusterRecord::getId))
			.pickupLocationCode("")
			.pickupAgencyCode("pickup-agency")
			.excludedSupplyingAgencyCodes(emptyList())
			.build();

		final var resolution = resolve(parameters);

		// Assert
		assertThat(resolution, allOf(
			notNullValue(),
			hasNoChosenItem()
		));

	}

	private Resolution resolve(ResolutionParameters parameters) {
		return singleValueFrom(patronRequestResolutionService.resolve(parameters));
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

	private SierraItem checkedOutItem(String id, String barcode) {
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

	private SierraItem checkedOutItem(String id, String barcode, Instant dueDate) {
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

	private SierraItem itemWithHolds(String itemWithHoldsId, String itemWithHoldsBarcode) {
		return SierraItem.builder()
			.id(itemWithHoldsId)
			.barcode(itemWithHoldsBarcode)
			.locationCode(ITEM_LOCATION_CODE)
			.statusCode("-")
			.holdCount(1)
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

	private ResolutionParameters standardParametersFor(ClusterRecord clusterRecord) {
		return ResolutionParameters.builder()
			.borrowingAgencyCode(BORROWING_AGENCY_CODE)
			.borrowingHostLmsCode(BORROWING_HOST_LMS_CODE)
			.bibClusterId(getValueOrNull(clusterRecord, ClusterRecord::getId))
			.pickupLocationCode(borrowingAgencyPickupLocationId)
			.pickupAgencyCode(getValueOrNull(borrowingAgency, DataAgency::getCode))
			.excludedSupplyingAgencyCodes(emptyList())
			.build();
	}

	private void allowUnavailableItems() {
		defineSetting(SELECT_UNAVAILABLE_ITEMS, true);
	}

	private void disallowUnavailableItems() {
		defineSetting(SELECT_UNAVAILABLE_ITEMS, false);
	}

	private void allowOwnLibraryBorrowing() {
		defineSetting(OWN_LIBRARY_BORROWING, true);
	}

	private void defineSetting(FunctionalSettingType settingType, boolean enabled) {
		consortiumFixture.createConsortiumWithFunctionalSetting(settingType, enabled);
	}

	private ClusterRecord createClusterRecord(UUID bibRecordId) {
		return clusterRecordFixture.createClusterRecord(randomUUID(), bibRecordId);
	}
}
