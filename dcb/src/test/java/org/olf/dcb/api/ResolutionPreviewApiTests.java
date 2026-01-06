package org.olf.dcb.api;

import static io.micronaut.http.HttpStatus.BAD_REQUEST;
import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.anyOf;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.hamcrest.Matcher;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockserver.client.MockServerClient;
import org.olf.dcb.core.api.ResolutionPreview;
import org.olf.dcb.core.interaction.sierra.SierraApiFixtureProvider;
import org.olf.dcb.core.interaction.sierra.SierraItem;
import org.olf.dcb.core.interaction.sierra.SierraItemsAPIFixture;
import org.olf.dcb.core.model.DataHostLms;
import org.olf.dcb.request.resolution.ResolutionParameters;
import org.olf.dcb.request.workflow.PresentableItem;
import org.olf.dcb.test.AgencyFixture;
import org.olf.dcb.test.BibRecordFixture;
import org.olf.dcb.test.ClusterRecordFixture;
import org.olf.dcb.test.HostLmsFixture;
import org.olf.dcb.test.ReferenceValueMappingFixture;

import io.micronaut.http.client.exceptions.HttpClientResponseException;
import jakarta.inject.Inject;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import services.k_int.interaction.sierra.FixedField;
import services.k_int.interaction.sierra.SierraTestUtils;
import services.k_int.test.mockserver.MockServerMicronautTest;

@Slf4j
@MockServerMicronautTest
@TestInstance(PER_CLASS)
class ResolutionPreviewApiTests {
	private final String CATALOGUING_HOST_LMS_CODE = "resolution-cataloguing";
	private final String CIRCULATING_HOST_LMS_CODE = "resolution-circulating";
	private final String BORROWING_HOST_LMS_CODE = "resolution-borrowing";

	private final String BORROWING_AGENCY_CODE = "borrowing-agency";

	private final String PICKUP_LOCATION_CODE = "pickup-location";
	private final String ITEM_LOCATION_CODE = "item-location";

	@Inject
	private ResolutionApiClient resolutionApiClient;

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

	private SierraItemsAPIFixture sierraItemsAPIFixture;

	private DataHostLms cataloguingHostLms;

	@BeforeAll
	@SneakyThrows
	public void beforeAll(MockServerClient mockServerClient) {
		final String HOST_LMS_BASE_URL = "https://resolution-preview.com";
		final String HOST_LMS_TOKEN = "resolution-system-token";
		final String HOST_LMS_KEY = "resolution-system-key";
		final String HOST_LMS_SECRET = "resolution-system-secret";

		SierraTestUtils.mockFor(mockServerClient, HOST_LMS_BASE_URL)
			.setValidCredentials(HOST_LMS_KEY, HOST_LMS_SECRET, HOST_LMS_TOKEN, 60);

		sierraItemsAPIFixture = sierraApiFixtureProvider.itemsApiFor(mockServerClient);

		hostLmsFixture.deleteAll();

		cataloguingHostLms = hostLmsFixture.createSierraHostLms(CATALOGUING_HOST_LMS_CODE, HOST_LMS_KEY,
			HOST_LMS_SECRET, HOST_LMS_BASE_URL, "item");

		hostLmsFixture.createSierraHostLms(CIRCULATING_HOST_LMS_CODE, "",
			"", "http://some-system", "item");

		hostLmsFixture.createSierraHostLms(BORROWING_HOST_LMS_CODE, "",
			"", "http://some-borrowing-system", "item");
	}

	@BeforeEach
	void beforeEach() {
		final var supplyingAgencyCode = "supplying-agency";

		clusterRecordFixture.deleteAll();
		referenceValueMappingFixture.deleteAll();
		agencyFixture.deleteAll();

		referenceValueMappingFixture.defineLocationToAgencyMapping(
			BORROWING_HOST_LMS_CODE, PICKUP_LOCATION_CODE, BORROWING_AGENCY_CODE);

		referenceValueMappingFixture.defineLocationToAgencyMapping(
			CATALOGUING_HOST_LMS_CODE, ITEM_LOCATION_CODE, supplyingAgencyCode);

		agencyFixture.defineAgency(supplyingAgencyCode, supplyingAgencyCode,
			hostLmsFixture.findByCode(CIRCULATING_HOST_LMS_CODE));

		agencyFixture.defineAgency(BORROWING_AGENCY_CODE, BORROWING_AGENCY_CODE,
			hostLmsFixture.findByCode(BORROWING_HOST_LMS_CODE));
	}

	@Test
	void shouldPreviewSuccessfulResolution() {
		// Arrange
		final var bibRecordId = randomUUID();

		final var clusterRecord = clusterRecordFixture.createClusterRecord(randomUUID(), bibRecordId);

		final var localBibId = "72745";

		bibRecordFixture.createBibRecord(bibRecordId, cataloguingHostLms.getId(),
			localBibId, clusterRecord);

		referenceValueMappingFixture.defineLocalToCanonicalItemTypeRangeMapping(
			CIRCULATING_HOST_LMS_CODE, 1, 1, "loanable-item");

		final var checkedOutItem = SierraItem.builder()
			.id("277573")
			.barcode("7364366535")
			.locationCode(ITEM_LOCATION_CODE)
			.statusCode("-")
			.dueDate(Instant.parse("2021-02-25T12:00:00Z"))
			.itemType("1")
			.fixedFields(Map.of(61, FixedField.builder().value("1").build()))
			.build();

		final var availableItem = SierraItem.builder()
			.id("6837432")
			.barcode("2643657655")
			.locationCode(ITEM_LOCATION_CODE)
			.statusCode("-")
			.itemType("1")
			.fixedFields(Map.of(61, FixedField.builder().value("1").build()))
			.build();

		sierraItemsAPIFixture.itemsForBibId(localBibId,
			List.of(checkedOutItem, availableItem));

		// Act
		final var resolutionPreview = resolutionApiClient.previewResolution(
			ResolutionParameters.builder()
				.borrowingAgencyCode(BORROWING_AGENCY_CODE)
				.borrowingHostLmsCode(BORROWING_HOST_LMS_CODE)
				.bibClusterId(clusterRecord.getId())
				.pickupLocationCode(PICKUP_LOCATION_CODE)
				.build());

		// Assert
		assertThat(resolutionPreview, allOf(
			notNullValue(),
			itemWasSelected(),
			hasSelectedItem(availableItem),
			hasItems(allOf(containsInAnyOrder(
				hasItem(availableItem),
				hasItem(checkedOutItem)
			))),
			hasProperty("filteredItems", allOf(containsInAnyOrder(
				hasItem(availableItem)
			))),
			hasProperty("sortedItems", allOf(contains(
				hasItem(availableItem)
			)))
		));
	}

	@Test
	void shouldPreviewUnsuccessfulResolution() {
		// Arrange
		final var bibRecordId = randomUUID();

		final var clusterRecord = clusterRecordFixture.createClusterRecord(randomUUID(), bibRecordId);

		final var localBibId = "46535";

		bibRecordFixture.createBibRecord(bibRecordId, cataloguingHostLms.getId(),
			localBibId, clusterRecord);

		sierraItemsAPIFixture.zeroItemsResponseForBibId(localBibId);

		// Act
		final var resolutionPreview = resolutionApiClient.previewResolution(
			ResolutionParameters.builder()
				.borrowingAgencyCode(BORROWING_AGENCY_CODE)
				.borrowingHostLmsCode(BORROWING_HOST_LMS_CODE)
				.bibClusterId(clusterRecord.getId())
				.pickupLocationCode(PICKUP_LOCATION_CODE)
				.build());

		// Assert
		assertThat(resolutionPreview, allOf(
			notNullValue(),
			noItemWasSelected(),
			hasNoSelectedItem(),
			hasNoItems(),
			hasNoFilteredItems(),
			hasNoSortedItems()
		));
	}

	@Test
	void shouldFailWhenClusterRecordCannotBeFound() {
		// Arrange
		final var clusterRecordId = randomUUID();

		// Act
		final var exception = assertThrows(HttpClientResponseException.class,
			() -> resolutionApiClient.previewResolution(
				ResolutionParameters.builder()
					.borrowingAgencyCode(BORROWING_AGENCY_CODE)
					.borrowingHostLmsCode(BORROWING_HOST_LMS_CODE)
					.bibClusterId(clusterRecordId)
					.pickupLocationCode(PICKUP_LOCATION_CODE)
					.build()));

		// Assert
		assertThat(exception, is(notNullValue()));

		final var response = exception.getResponse();

		assertThat("Should respond with a bad request status",
			response.getStatus(), is(BAD_REQUEST));

		final var optionalBody = response.getBody(String.class);

		assertThat("Response should have a body", optionalBody.isPresent(), is(true));

		assertThat(optionalBody.get(), is("Cannot find cluster record for: " + clusterRecordId));
	}

	private static Matcher<ResolutionPreview> itemWasSelected() {
		return hasProperty("itemWasSelected", is(true));
	}

	private static Matcher<ResolutionPreview> noItemWasSelected() {
		return hasProperty("itemWasSelected", is(false));
	}

	private static Matcher<ResolutionPreview> hasSelectedItem(SierraItem expectedItem) {
		return hasProperty("selectedItem", hasItem(expectedItem));
	}

	private static Matcher<ResolutionPreview> hasNoSelectedItem() {
		return hasProperty("selectedItem", is(nullValue()));
	}

	private static Matcher<ResolutionPreview> hasItems(
		Matcher<Iterable<? extends PresentableItem>> matcher) {

		return hasProperty("allItemsFromAvailability", matcher);
	}

	private static Matcher<ResolutionPreview> hasNoItems() {
		return hasProperty("allItemsFromAvailability", anyOf(nullValue(), empty()));
	}

	private static Matcher<ResolutionPreview> hasNoFilteredItems() {
		return hasProperty("filteredItems", anyOf(nullValue(), empty()));
	}

	private static Matcher<ResolutionPreview> hasNoSortedItems() {
		return hasProperty("sortedItems", anyOf(nullValue(), empty()));
	}

	private static Matcher<PresentableItem> hasItem(SierraItem expectedItem) {
		return hasBarcode(expectedItem.getBarcode());
	}

	private static Matcher<PresentableItem> hasBarcode(String expectedBarcode) {
		return hasProperty("barcode", is(expectedBarcode));
	}
}
