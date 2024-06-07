package org.olf.dcb.request.fulfilment;

import static java.util.UUID.randomUUID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;

import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockserver.client.MockServerClient;
import org.olf.dcb.core.interaction.sierra.SierraApiFixtureProvider;
import org.olf.dcb.core.interaction.sierra.SierraItem;
import org.olf.dcb.core.interaction.sierra.SierraItemsAPIFixture;
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

import jakarta.inject.Inject;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import services.k_int.interaction.sierra.SierraTestUtils;
import services.k_int.test.mockserver.MockServerMicronautTest;

@Slf4j
@MockServerMicronautTest
@TestInstance(PER_CLASS)
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

		sierraItemsAPIFixture.itemsForBibId(sourceRecordId, List.of(
			availableItem(onlyAvailableItemId, onlyAvailableItemBarcode)
		));

		final var localPatronId = "465367";

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

	private List<CheckResult> check(PlacePatronRequestCommand command) {
		return singleValueFrom(check.check(command));
	}

	private SierraItem availableItem(String id, String barcode) {
		return SierraItem.builder()
			.id(id)
			.barcode(barcode)
			.locationCode(ITEM_LOCATION_CODE)
			.statusCode("-")
			.build();
	}
}
