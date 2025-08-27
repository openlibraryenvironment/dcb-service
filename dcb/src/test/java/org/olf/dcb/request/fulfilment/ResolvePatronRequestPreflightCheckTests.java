package org.olf.dcb.request.fulfilment;

import static java.util.UUID.randomUUID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockserver.client.MockServerClient;
import org.olf.dcb.core.interaction.sierra.SierraApiFixtureProvider;
import org.olf.dcb.core.interaction.sierra.SierraItem;
import org.olf.dcb.core.interaction.sierra.SierraItemsAPIFixture;
import org.olf.dcb.core.interaction.sierra.SierraPatronsAPIFixture;
import org.olf.dcb.core.model.DataHostLms;
import org.olf.dcb.request.fulfilment.PlacePatronRequestCommand.Citation;
import org.olf.dcb.request.fulfilment.PlacePatronRequestCommand.PickupLocation;
import org.olf.dcb.request.fulfilment.PlacePatronRequestCommand.Requestor;
import org.olf.dcb.test.AgencyFixture;
import org.olf.dcb.test.BibRecordFixture;
import org.olf.dcb.test.ClusterRecordFixture;
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
@Property(name = "dcb.requests.preflight-checks.resolve-patron-request.enabled", value = "true")
class ResolvePatronRequestPreflightCheckTests extends AbstractPreflightCheckTests {
	private final String CATALOGUING_HOST_LMS_CODE = "resolution-cataloguing";
	private final String CIRCULATING_HOST_LMS_CODE = "resolution-circulating";
	private final String BORROWING_HOST_LMS_CODE = "resolution-borrowing";

	private final String BORROWING_AGENCY_CODE = "borrowing-agency";
	private final String SUPPLYING_AGENCY_CODE = "supplying-agency";

	private final String PICKUP_LOCATION_CODE = "pickup-location";
	private final String ITEM_LOCATION_CODE = "item-location";

	@Inject
	private ResolvePatronRequestPreflightCheck check;

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
	private SierraPatronsAPIFixture sierraPatronsAPIFixture;

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
		sierraPatronsAPIFixture = sierraApiFixtureProvider.patronsApiFor(mockServerClient);

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

		agencyFixture.defineAgency(BORROWING_AGENCY_CODE, BORROWING_AGENCY_CODE,
			hostLmsFixture.findByCode(BORROWING_HOST_LMS_CODE));

		agencyFixture.defineAgency(SUPPLYING_AGENCY_CODE, SUPPLYING_AGENCY_CODE,
			hostLmsFixture.findByCode(CIRCULATING_HOST_LMS_CODE));
	}

	@Test
	void shouldPassWhenAnItemCanBeSelected() {
		// Arrange
		final var bibRecordId = randomUUID();

		final var clusterRecord = clusterRecordFixture.createClusterRecord(randomUUID(), bibRecordId);

		final var sourceRecordId = "5473765";

		bibRecordFixture.createBibRecord(bibRecordId, cataloguingHostLms.getId(),
				sourceRecordId, clusterRecord);

		final var onlyAvailableItemId = "352545";
		final var onlyAvailableItemBarcode = "82365396";

		referenceValueMappingFixture.defineLocalToCanonicalItemTypeRangeMapping(
			"resolution-cataloguing", 1, 1, "loanable-item");

		sierraItemsAPIFixture.itemsForBibId(sourceRecordId, List.of(
			availableItem(onlyAvailableItemId, onlyAvailableItemBarcode,
				ITEM_LOCATION_CODE)
		));

		final var localPatronId = definePatron("465367");

		// Act
		final var command = PlacePatronRequestCommand.builder()
			.requestor(Requestor.builder()
				.localSystemCode(BORROWING_HOST_LMS_CODE)
				.localId(localPatronId)
				.build())
			.citation(Citation.builder()
				.bibClusterId(clusterRecord.getId())
				.build())
			.pickupLocation(PickupLocation.builder()
				.code(PICKUP_LOCATION_CODE)
				.build())
			.build();

		final var results = check(command);

		// Assert
		assertThat(results, containsInAnyOrder(passedCheck()));
	}

	@Test
	void shouldFailWhenNoItemCanBeSelected() {
		// Arrange
		final var bibRecordId = randomUUID();

		final var clusterRecord = clusterRecordFixture.createClusterRecord(randomUUID(), bibRecordId);

		final var clusterRecordId = clusterRecord.getId();

		final var sourceRecordId = "3545674";

		bibRecordFixture.createBibRecord(bibRecordId, cataloguingHostLms.getId(),
			sourceRecordId, clusterRecord);

		sierraItemsAPIFixture.zeroItemsResponseForBibId(sourceRecordId);

		final var localPatronId = "2645637";

		definePatron(localPatronId);

		// Act
		final var command = PlacePatronRequestCommand.builder()
			.requestor(Requestor.builder()
				.localSystemCode(BORROWING_HOST_LMS_CODE)
				.localId(localPatronId)
				.build())
			.citation(Citation.builder()
				.bibClusterId(clusterRecordId)
				.build())
			.pickupLocation(PickupLocation.builder()
				.code(PICKUP_LOCATION_CODE)
				.build())
			.build();

		final var results = check(command);

		// Assert
		assertThat(results, containsInAnyOrder(
			failedCheck("NO_ITEM_SELECTABLE_FOR_REQUEST",
				"Patron request for cluster record \"%s\" could not be resolved to an item"
					.formatted(clusterRecordId))
		));
	}

	@Test
	void shouldFailWhenOnlyItemIsFromSameAgencyAsBorrower() {
		// Arrange
		final var bibRecordId = randomUUID();

		final var clusterRecord = clusterRecordFixture.createClusterRecord(randomUUID(), bibRecordId);

		final var clusterRecordId = clusterRecord.getId();

		final var sourceRecordId = "625252";

		bibRecordFixture.createBibRecord(bibRecordId, cataloguingHostLms.getId(),
			sourceRecordId, clusterRecord);

		final var onlyAvailableItemId = "352545";
		final var onlyAvailableItemBarcode = "82365396";
		final var itemLocationCode = "borrowing-location";

		sierraItemsAPIFixture.itemsForBibId(sourceRecordId, List.of(
			availableItem(onlyAvailableItemId, onlyAvailableItemBarcode,
				itemLocationCode)
		));

		referenceValueMappingFixture.defineLocationToAgencyMapping(BORROWING_HOST_LMS_CODE,
			itemLocationCode, BORROWING_AGENCY_CODE);

		final var localPatronId = "353663";

		definePatron(localPatronId);

		// Act
		final var command = PlacePatronRequestCommand.builder()
			.requestor(Requestor.builder()
				.localSystemCode(BORROWING_HOST_LMS_CODE)
				.localId(localPatronId)
				.build())
			.citation(Citation.builder()
				.bibClusterId(clusterRecordId)
				.build())
			.pickupLocation(PickupLocation.builder()
				.code(PICKUP_LOCATION_CODE)
				.build())
			.build();

		final var results = check(command);

		// Assert
		assertThat(results, containsInAnyOrder(
			failedCheck("NO_ITEM_SELECTABLE_FOR_REQUEST",
				"Patron request for cluster record \"%s\" could not be resolved to an item"
					.formatted(clusterRecordId))
		));
	}

	@Test
	void shouldFailWhenOnlyItemIsNotManuallySelected() {
		// Arrange
		final var bibRecordId = randomUUID();

		final var clusterRecord = clusterRecordFixture.createClusterRecord(randomUUID(), bibRecordId);

		final var clusterRecordId = clusterRecord.getId();

		final var sourceRecordId = "625252";

		bibRecordFixture.createBibRecord(bibRecordId, cataloguingHostLms.getId(),
			sourceRecordId, clusterRecord);

		final var onlyAvailableItemId = "274625";
		final var onlyAvailableItemBarcode = "92565476";

		referenceValueMappingFixture.defineLocalToCanonicalItemTypeRangeMapping(
			"resolution-cataloguing", 1, 1, "loanable-item");

		sierraItemsAPIFixture.itemsForBibId(sourceRecordId, List.of(
			availableItem(onlyAvailableItemId, onlyAvailableItemBarcode, ITEM_LOCATION_CODE)
		));

		final var localPatronId = "547265";

		definePatron(localPatronId);

		// Act
		final var command = PlacePatronRequestCommand.builder()
			.requestor(Requestor.builder()
				.localSystemCode(BORROWING_HOST_LMS_CODE)
				.localId(localPatronId)
				.build())
			.citation(Citation.builder()
				.bibClusterId(clusterRecordId)
				.build())
			.item(PlacePatronRequestCommand.Item.builder()
				.localId("4322424")
				.agencyCode(SUPPLYING_AGENCY_CODE)
				.localSystemCode(CIRCULATING_HOST_LMS_CODE)
				.build())
			.pickupLocation(PickupLocation.builder()
				.code(PICKUP_LOCATION_CODE)
				.build())
			.build();

		final var results = check(command);

		// Assert
		assertThat(results, containsInAnyOrder(
			failedCheck("NO_ITEM_SELECTABLE_FOR_REQUEST",
				"Patron request for cluster record \"%s\" could not be resolved to an item"
					.formatted(clusterRecordId))
		));
	}

	@Test
	void shouldFailWhenClusterRecordCannotBeFound() {
		// Arrange
		final var clusterRecordId = randomUUID();

		final var localPatronId = "563653";

		definePatron(localPatronId);

		// Act
		final var command = PlacePatronRequestCommand.builder()
			.requestor(Requestor.builder()
				.localSystemCode(BORROWING_HOST_LMS_CODE)
				.localId(localPatronId)
				.build())
			.citation(Citation.builder()
				.bibClusterId(clusterRecordId)
				.build())
			.pickupLocation(PickupLocation.builder()
				.code(PICKUP_LOCATION_CODE)
				.build())
			.build();

		final var results = check(command);

		// Assert
		assertThat(results, containsInAnyOrder(
			failedCheck("CLUSTER_RECORD_NOT_FOUND",
				"Cluster record \"%s\" cannot be found".formatted(clusterRecordId))
		));
	}

	@Test
	void shouldFailWhenPatronCannotBeFoundInHostLms() {
		// Arrange
		final var bibRecordId = randomUUID();

		final var clusterRecord = clusterRecordFixture.createClusterRecord(randomUUID(), bibRecordId);

		final var clusterRecordId = clusterRecord.getId();

		final var sourceRecordId = "874626";

		bibRecordFixture.createBibRecord(bibRecordId, cataloguingHostLms.getId(),
			sourceRecordId, clusterRecord);

		final var localPatronId = "365573";

		sierraPatronsAPIFixture.noRecordsFoundWhenGettingPatronByLocalId(localPatronId);
		sierraPatronsAPIFixture.patronNotFoundResponse("u", localPatronId);

		// Act
		final var command = PlacePatronRequestCommand.builder()
			.requestor(Requestor.builder()
				.localSystemCode(BORROWING_HOST_LMS_CODE)
				.localId(localPatronId)
				.build())
			.citation(Citation.builder()
				.bibClusterId(clusterRecordId)
				.build())
			.pickupLocation(PickupLocation.builder()
				.code(PICKUP_LOCATION_CODE)
				.build())
			.build();

		final var results = check(command);

		// Assert
		assertThat(results, containsInAnyOrder(
			failedCheck("PATRON_NOT_FOUND",
				"Patron \"%s\" is not recognised in \"%s\""
					.formatted(localPatronId, BORROWING_HOST_LMS_CODE))
		));
	}

	@Test
	void shouldFailWhenPatronIsNotAssociatedWithAgency() {
		// Arrange
		final var bibRecordId = randomUUID();

		final var clusterRecord = clusterRecordFixture.createClusterRecord(randomUUID(), bibRecordId);

		final var clusterRecordId = clusterRecord.getId();

		final var sourceRecordId = "874626";

		bibRecordFixture.createBibRecord(bibRecordId, cataloguingHostLms.getId(),
			sourceRecordId, clusterRecord);

		final var localPatronId = "8292567";
		final var localPatronType = 15;

		sierraPatronsAPIFixture.getPatronByLocalIdSuccessResponse(localPatronId,
			SierraPatronsAPIFixture.Patron.builder()
				.id(Integer.parseInt(localPatronId))
				.patronType(localPatronType)
				.homeLibraryCode("home-library")
				.barcodes(List.of("27536633"))
				.names(List.of("Bob"))
				.build());

		referenceValueMappingFixture.defineNumericPatronTypeRangeMapping(
			BORROWING_HOST_LMS_CODE, localPatronType, localPatronType, "DCB", "UNDERGRAD");

		// Act
		final var command = PlacePatronRequestCommand.builder()
			.requestor(Requestor.builder()
				.localSystemCode(BORROWING_HOST_LMS_CODE)
				.localId(localPatronId)
				.build())
			.citation(Citation.builder()
				.bibClusterId(clusterRecordId)
				.build())
			.pickupLocation(PickupLocation.builder()
				.code(PICKUP_LOCATION_CODE)
				.build())
			.build();

		final var results = check(command);

		// Assert
		assertThat(results, containsInAnyOrder(
			failedCheck("PATRON_NOT_ASSOCIATED_WITH_AGENCY",
				"Patron \"%s\" with home library code \"%s\" from \"%s\" is not associated with an agency"
					.formatted(localPatronId, "home-library", BORROWING_HOST_LMS_CODE))
		));
	}

	@Test
	void shouldFailWhenLocalPatronTypeIsNotMappedToCanonicalPatronType() {
		// Arrange
		final var bibRecordId = randomUUID();

		final var clusterRecord = clusterRecordFixture.createClusterRecord(randomUUID(), bibRecordId);

		final var clusterRecordId = clusterRecord.getId();

		final var sourceRecordId = "356354";

		bibRecordFixture.createBibRecord(bibRecordId, cataloguingHostLms.getId(),
			sourceRecordId, clusterRecord);

		final var localPatronId = "736553";
		final var unmappedLocalPatronType = 35;
		final var homeLibraryCode = "home-library";

		sierraPatronsAPIFixture.getPatronByLocalIdSuccessResponse(localPatronId,
			SierraPatronsAPIFixture.Patron.builder()
				.id(Integer.parseInt(localPatronId))
				.patronType(unmappedLocalPatronType)
				.homeLibraryCode(homeLibraryCode)
				.barcodes(List.of("27536633"))
				.names(List.of("Bob"))
				.build());

		referenceValueMappingFixture.defineLocationToAgencyMapping(BORROWING_HOST_LMS_CODE, homeLibraryCode, BORROWING_AGENCY_CODE);

		// Act
		final var command = PlacePatronRequestCommand.builder()
			.requestor(Requestor.builder()
				.localSystemCode(BORROWING_HOST_LMS_CODE)
				.localId(localPatronId)
				.build())
			.citation(Citation.builder()
				.bibClusterId(clusterRecordId)
				.build())
			.pickupLocation(PickupLocation.builder()
				.code(PICKUP_LOCATION_CODE)
				.build())
			.build();

		final var results = check(command);

		// Assert
		assertThat(results, containsInAnyOrder(
			failedCheck("PATRON_TYPE_NOT_MAPPED",
				"Local patron type \"%d\" from \"%s\" is not mapped to a DCB canonical patron type"
					.formatted(unmappedLocalPatronType, BORROWING_HOST_LMS_CODE))
		));
	}

	@Test
	void shouldFailWhenNoLocalPatronTypeIsDefined() {
		// Arrange
		final var bibRecordId = randomUUID();

		final var clusterRecord = clusterRecordFixture.createClusterRecord(randomUUID(), bibRecordId);

		final var clusterRecordId = clusterRecord.getId();

		final var sourceRecordId = "987531";

		bibRecordFixture.createBibRecord(bibRecordId, cataloguingHostLms.getId(),
			sourceRecordId, clusterRecord);

		final var localPatronId = "257255";
		final var homeLibraryCode = "home-library";

		sierraPatronsAPIFixture.getPatronByLocalIdSuccessResponse(localPatronId,
			SierraPatronsAPIFixture.Patron.builder()
				.id(Integer.parseInt(localPatronId))
				.homeLibraryCode(homeLibraryCode)
				.barcodes(List.of("27536633"))
				.names(List.of("Bob"))
				.build());

		referenceValueMappingFixture.defineLocationToAgencyMapping(BORROWING_HOST_LMS_CODE, homeLibraryCode, BORROWING_AGENCY_CODE);

		// Act
		final var command = PlacePatronRequestCommand.builder()
			.requestor(Requestor.builder()
				.localSystemCode(BORROWING_HOST_LMS_CODE)
				.localId(localPatronId)
				.build())
			.citation(Citation.builder()
				.bibClusterId(clusterRecordId)
				.build())
			.pickupLocation(PickupLocation.builder()
				.code(PICKUP_LOCATION_CODE)
				.build())
			.build();


		final var results = check(command);

		// Assert
		assertThat(results, containsInAnyOrder(
			failedCheck("LOCAL_PATRON_TYPE_IS_NON_NUMERIC",
				"Local patron \"%s\" from \"%s\" has non-numeric patron type \"null\""
					.formatted(localPatronId, BORROWING_HOST_LMS_CODE))
		));
	}

	@Test
	void shouldFailWhenHostLmsIsNotRecognised() {
		// Arrange
		final var unknownHostLmsCode = "unknown-host-lms";

		// Act
		final var command = PlacePatronRequestCommand.builder()
			.requestor(Requestor.builder()
				.localSystemCode(unknownHostLmsCode)
				.localId("6545362")
				.build())
			.citation(Citation.builder()
				.bibClusterId(UUID.randomUUID())
				.build())
			.pickupLocation(PickupLocation.builder()
				.code(PICKUP_LOCATION_CODE)
				.build())
			.build();

		final var results = check(command);

		// Assert
		assertThat(results, containsInAnyOrder(
			failedCheck("UNKNOWN_BORROWING_HOST_LMS",
				"\"%s\" is not a recognised Host LMS".formatted(unknownHostLmsCode))
		));
	}

	private List<CheckResult> check(PlacePatronRequestCommand command) {
		return singleValueFrom(check.check(command));
	}

	private SierraItem availableItem(String id, String barcode,
		String locationCode) {

		return SierraItem.builder()
			.id(id)
			.barcode(barcode)
			.locationCode(locationCode)
			.statusCode("-")
			.itemType("1")
			.fixedFields(Map.of(61, FixedField.builder().value("1").build()))
			.build();
	}

	private String definePatron(String localPatronId) {
		final var homeLibraryCode = "home-library";
		final var localPatronType = 15;

		sierraPatronsAPIFixture.getPatronByLocalIdSuccessResponse(localPatronId,
			SierraPatronsAPIFixture.Patron.builder()
				.id(Integer.parseInt(localPatronId))
				.patronType(localPatronType)
				.homeLibraryCode(homeLibraryCode)
				.barcodes(List.of("647647746"))
				.names(List.of("Bob"))
				.build());

		referenceValueMappingFixture.defineNumericPatronTypeRangeMapping(
			BORROWING_HOST_LMS_CODE, localPatronType, localPatronType, "DCB", "UNDERGRAD");

		referenceValueMappingFixture.defineLocationToAgencyMapping(BORROWING_HOST_LMS_CODE, homeLibraryCode, BORROWING_AGENCY_CODE);

		return localPatronId;
	}
}
