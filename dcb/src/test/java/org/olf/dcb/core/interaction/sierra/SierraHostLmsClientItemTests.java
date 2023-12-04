package org.olf.dcb.core.interaction.sierra;

import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.olf.dcb.core.model.ItemStatusCode.AVAILABLE;
import static org.olf.dcb.core.model.ItemStatusCode.CHECKED_OUT;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;
import static org.olf.dcb.test.matchers.ItemMatchers.hasAgencyCode;
import static org.olf.dcb.test.matchers.ItemMatchers.hasAgencyName;
import static org.olf.dcb.test.matchers.ItemMatchers.hasBarcode;
import static org.olf.dcb.test.matchers.ItemMatchers.hasCallNumber;
import static org.olf.dcb.test.matchers.ItemMatchers.hasCanonicalItemType;
import static org.olf.dcb.test.matchers.ItemMatchers.hasDueDate;
import static org.olf.dcb.test.matchers.ItemMatchers.hasHoldCount;
import static org.olf.dcb.test.matchers.ItemMatchers.hasHostLmsCode;
import static org.olf.dcb.test.matchers.ItemMatchers.hasLocalBibId;
import static org.olf.dcb.test.matchers.ItemMatchers.hasLocalId;
import static org.olf.dcb.test.matchers.ItemMatchers.hasLocalItemType;
import static org.olf.dcb.test.matchers.ItemMatchers.hasLocalItemTypeCode;
import static org.olf.dcb.test.matchers.ItemMatchers.hasLocation;
import static org.olf.dcb.test.matchers.ItemMatchers.hasNoAgencyCode;
import static org.olf.dcb.test.matchers.ItemMatchers.hasNoAgencyName;
import static org.olf.dcb.test.matchers.ItemMatchers.hasNoDueDate;
import static org.olf.dcb.test.matchers.ItemMatchers.hasStatus;
import static org.olf.dcb.test.matchers.ItemMatchers.isNotDeleted;
import static org.olf.dcb.test.matchers.ItemMatchers.suppressionUnknown;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockserver.client.MockServerClient;
import org.olf.dcb.core.interaction.HostLmsClient;
import org.olf.dcb.core.model.BibRecord;
import org.olf.dcb.core.model.Item;
import org.olf.dcb.test.AgencyFixture;
import org.olf.dcb.test.HostLmsFixture;
import org.olf.dcb.test.NumericRangeMappingFixture;
import org.olf.dcb.test.ReferenceValueMappingFixture;

import jakarta.inject.Inject;
import lombok.SneakyThrows;
import services.k_int.interaction.sierra.FixedField;
import services.k_int.interaction.sierra.SierraTestUtils;
import services.k_int.interaction.sierra.items.Location;
import services.k_int.interaction.sierra.items.SierraItem;
import services.k_int.interaction.sierra.items.Status;
import services.k_int.test.mockserver.MockServerMicronautTest;

@MockServerMicronautTest
@TestInstance(PER_CLASS)
class SierraHostLmsClientItemTests {
	private static final String HOST_LMS_CODE = "sierra-item-api-tests";

	@Inject
	private SierraApiFixtureProvider sierraApiFixtureProvider;

	@Inject
	private HostLmsFixture hostLmsFixture;
	@Inject
	private ReferenceValueMappingFixture referenceValueMappingFixture;
	@Inject
	private AgencyFixture agencyFixture;
	@Inject
	private NumericRangeMappingFixture numericRangeMappingFixture;

	private SierraItemsAPIFixture sierraItemsAPIFixture;

	@BeforeAll
	public void beforeAll(MockServerClient mockServerClient) {
		final String TOKEN = "test-token";
		final String BASE_URL = "https://item-api-tests.com";
		final String KEY = "item-key";
		final String SECRET = "item-secret";

		SierraTestUtils.mockFor(mockServerClient, BASE_URL)
			.setValidCredentials(KEY, SECRET, TOKEN, 60);

		sierraItemsAPIFixture = sierraApiFixtureProvider.itemsApiFor(mockServerClient);

		hostLmsFixture.deleteAll();

		hostLmsFixture.createSierraHostLms(HOST_LMS_CODE, KEY, SECRET, BASE_URL, "item");
	}

	@BeforeEach
	void beforeEach() {
		referenceValueMappingFixture.deleteAll();
		agencyFixture.deleteAll();
	}

	@Test
	@SneakyThrows
	void sierraCanRespondWithMultipleItems() {
		// Arrange
		sierraItemsAPIFixture.itemsForBibId("65423515", List.of(
			SierraItem.builder()
				.id("f2010365-e1b1-4a5d-b431-a3c65b5f23fb")
				.barcode("9849123490")
				.callNumber("BL221 .C48")
				.status(Status.builder()
					.code("-")
					.duedate("2023-04-22T15:55:13Z")
					.build())
				.location(Location.builder()
					.name("King 5th Floor")
					.code("ab5")
					.build())
				.itemType("999")
				.fixedFields(Map.of(
					61, FixedField.builder().value("999").build()
				))
				.holdCount(0)
				.deleted(false)
				.build(),
				SierraItem.builder()
					.id("c5bc9cd0-fc23-48be-9d52-647cea8c63ca")
					.barcode("30800005315459")
					.callNumber("HX157 .H8")
					.status(Status.builder()
						.code("-")
						.build())
					.location(Location.builder()
						.name("King 7th Floor")
						.code("ab7")
						.build())
					.itemType("999")
					.fixedFields(Map.of(
						61, FixedField.builder().value("999").build()
					))
					.holdCount(1)
					.deleted(false)
					.build(),
			SierraItem.builder()
				.id("69415d0a-ace5-49e4-96fd-f63855235bf0")
				.barcode("30800005208449")
				.callNumber("HC336.2 .S74 1969")
				.status(Status.builder()
					.code("-")
					.build())
				.location(Location.builder()
					.name("King 7th Floor")
					.code("ab7")
					.build())
				.itemType("999")
				.fixedFields(Map.of(
					61, FixedField.builder().value("999").build()
				))
				.holdCount(2)
				.deleted(false)
				.build()
		));

		numericRangeMappingFixture.createMapping(HOST_LMS_CODE, "ItemType", 999L, 999L, "DCB", "BKM");

		agencyFixture.defineAgency("sierra-agency", "Sierra Agency");

		referenceValueMappingFixture.defineLocationToAgencyMapping(HOST_LMS_CODE, "ab5", "sierra-agency");

		// Act
		final var client = hostLmsFixture.createClient(HOST_LMS_CODE);

		final var items = getItems(client, "65423515");

		assertThat(items, containsInAnyOrder(
			allOf(
				hasLocalId("f2010365-e1b1-4a5d-b431-a3c65b5f23fb"),
				hasBarcode("9849123490"),
				hasCallNumber("BL221 .C48"),
				hasStatus(CHECKED_OUT),
				hasDueDate("2023-04-22T15:55:13Z"),
				hasLocation("King 5th Floor", "ab5"),
				hasLocalBibId("65423515"),
				hasLocalItemType("999"),
				hasLocalItemTypeCode("999"),
				hasCanonicalItemType("BKM"),
				hasHoldCount(0),
				hasHostLmsCode(HOST_LMS_CODE),
				hasAgencyCode("sierra-agency"),
				hasAgencyName("Sierra Agency"),
				suppressionUnknown(),
				isNotDeleted()
			),
			allOf(
				hasLocalId("c5bc9cd0-fc23-48be-9d52-647cea8c63ca"),
				hasBarcode("30800005315459"),
				hasCallNumber("HX157 .H8"),
				hasStatus(AVAILABLE),
				hasNoDueDate(),
				hasLocation("King 7th Floor", "ab7"),
				hasLocalBibId("65423515"),
				hasLocalItemType("999"),
				hasLocalItemTypeCode("999"),
				hasCanonicalItemType("BKM"),
				hasHoldCount(1),
				hasHostLmsCode(HOST_LMS_CODE),
				hasNoAgencyCode(),
				hasNoAgencyName(),
				suppressionUnknown(),
				isNotDeleted()
			),
			allOf(
				hasLocalId("69415d0a-ace5-49e4-96fd-f63855235bf0"),
				hasBarcode("30800005208449"),
				hasCallNumber("HC336.2 .S74 1969"),
				hasStatus(AVAILABLE),
				hasNoDueDate(),
				hasLocation("King 7th Floor", "ab7"),
				hasLocalBibId("65423515"),
				hasLocalItemType("999"),
				hasLocalItemTypeCode("999"),
				hasCanonicalItemType("BKM"),
				hasHoldCount(2),
				hasHostLmsCode(HOST_LMS_CODE),
				hasNoAgencyCode(),
				hasNoAgencyName(),
				suppressionUnknown(),
				isNotDeleted()
			)
		));
	}

	@Test
	void shouldProvideNoItemsWhenSierraRespondsWithNoRecordsFoundError() {
		sierraItemsAPIFixture.zeroItemsResponseForBibId("87878325");

		final var client = hostLmsFixture.createClient(HOST_LMS_CODE);

		final var items = getItems(client,"87878325");

		assertThat("Should have no items", items, is(empty()));
	}

	private static List<Item> getItems(HostLmsClient client, String sourceRecordId) {
		return singleValueFrom(client.getItems(BibRecord.builder()
			.sourceRecordId(sourceRecordId)
			.build()));
	}
}
