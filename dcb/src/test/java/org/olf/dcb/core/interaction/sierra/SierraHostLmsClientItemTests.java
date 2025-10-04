package org.olf.dcb.core.interaction.sierra;

import static java.util.Collections.emptyList;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
import static org.olf.dcb.test.matchers.ItemMatchers.hasNoAgency;
import static org.olf.dcb.test.matchers.ItemMatchers.hasNoDueDate;
import static org.olf.dcb.test.matchers.ItemMatchers.hasNoHostLmsCode;
import static org.olf.dcb.test.matchers.ItemMatchers.hasNoParsedVolumeStatement;
import static org.olf.dcb.test.matchers.ItemMatchers.hasNoRawVolumeStatement;
import static org.olf.dcb.test.matchers.ItemMatchers.hasSourceHostLmsCode;
import static org.olf.dcb.test.matchers.ItemMatchers.hasStatus;
import static org.olf.dcb.test.matchers.ItemMatchers.isNotDeleted;
import static org.olf.dcb.test.matchers.ItemMatchers.isNotSuppressed;
import static org.olf.dcb.test.matchers.interaction.HttpResponseProblemMatchers.hasMessageForRequest;
import static org.olf.dcb.test.matchers.interaction.HttpResponseProblemMatchers.hasNoResponseBody;
import static org.olf.dcb.test.matchers.interaction.HttpResponseProblemMatchers.hasRequestMethod;
import static org.olf.dcb.test.matchers.interaction.HttpResponseProblemMatchers.hasResponseStatusCode;

import java.time.Instant;
import java.util.List;

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
import org.zalando.problem.ThrowableProblem;

import jakarta.inject.Inject;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import services.k_int.interaction.sierra.SierraTestUtils;
import services.k_int.test.mockserver.MockServerMicronautTest;

@Slf4j
@MockServerMicronautTest
@TestInstance(PER_CLASS)
class SierraHostLmsClientItemTests {
	private static final String CATALOGUING_HOST_LMS_CODE = "sierra-item-cataloguing";
	private static final String CIRCULATING_HOST_LMS_CODE = "sierra-item-circulating";

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

		final var sierraLoginFixture = sierraApiFixtureProvider.loginFixtureFor(mockServerClient);

		sierraLoginFixture.failLoginsForAnyOtherCredentials(KEY, SECRET);

		hostLmsFixture.deleteAll();

		hostLmsFixture.createSierraHostLms(CATALOGUING_HOST_LMS_CODE, KEY, SECRET, BASE_URL, "item");
		hostLmsFixture.createSierraHostLms(CIRCULATING_HOST_LMS_CODE, KEY, SECRET, BASE_URL, "item");
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
				.statusCode("-")
				.dueDate(Instant.parse("2023-04-22T15:55:13Z"))
				.locationName("King 5th Floor")
				.locationCode("ab5")
				.itemType("999")
				.holdCount(0)
				.deleted(false)
				.build(),
			SierraItem.builder()
				.id("c5bc9cd0-fc23-48be-9d52-647cea8c63ca")
				.barcode("30800005315459")
				.callNumber("HX157 .H8")
				.statusCode("-")
				.locationName("King 7th Floor")
				.locationCode("ab7")
				.itemType("999")
				.holdCount(1)
				.deleted(false)
				.build(),
			SierraItem.builder()
				.id("69415d0a-ace5-49e4-96fd-f63855235bf0")
				.barcode("30800005208449")
				.callNumber("HC336.2 .S74 1969")
				.statusCode("-")
				.locationName("King 7th Floor")
				.locationCode("ab7")
				.itemType("999")
				.holdCount(2)
				.deleted(false)
				.build()
		));

		numericRangeMappingFixture.createMapping(CIRCULATING_HOST_LMS_CODE,
			"ItemType", 999L, 999L, "DCB", "BKM");

		agencyFixture.defineAgency("sierra-agency", "Sierra Agency",
			hostLmsFixture.findByCode(CIRCULATING_HOST_LMS_CODE));

		referenceValueMappingFixture.defineLocationToAgencyMapping(
			CATALOGUING_HOST_LMS_CODE, "ab5", "sierra-agency");

		// Act
		final var client = hostLmsFixture.createClient(CATALOGUING_HOST_LMS_CODE);

		final var items = getItems(client, "65423515");

		assertThat(items, containsInAnyOrder(
			allOf(
				hasLocalId("f2010365-e1b1-4a5d-b431-a3c65b5f23fb"),
				hasSourceHostLmsCode(CATALOGUING_HOST_LMS_CODE),
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
				hasAgencyCode("sierra-agency"),
				hasAgencyName("Sierra Agency"),
				hasHostLmsCode(CIRCULATING_HOST_LMS_CODE),
				hasNoRawVolumeStatement(),
				hasNoParsedVolumeStatement(),
				isNotSuppressed(),
				isNotDeleted()
			),
			allOf(
				hasLocalId("c5bc9cd0-fc23-48be-9d52-647cea8c63ca"),
				hasSourceHostLmsCode(CATALOGUING_HOST_LMS_CODE),
				hasBarcode("30800005315459"),
				hasCallNumber("HX157 .H8"),
				hasStatus(AVAILABLE),
				hasNoDueDate(),
				hasLocation("King 7th Floor", "ab7"),
				hasLocalBibId("65423515"),
				hasLocalItemType("999"),
				hasLocalItemTypeCode("999"),
				hasCanonicalItemType("UNKNOWN - Item has no owning context"),
				hasHoldCount(1),
				hasNoAgency(),
				hasNoHostLmsCode(),
				hasNoRawVolumeStatement(),
				hasNoParsedVolumeStatement(),
				isNotSuppressed(),
				isNotDeleted()
			),
			allOf(
				hasLocalId("69415d0a-ace5-49e4-96fd-f63855235bf0"),
				hasSourceHostLmsCode(CATALOGUING_HOST_LMS_CODE),
				hasBarcode("30800005208449"),
				hasCallNumber("HC336.2 .S74 1969"),
				hasStatus(AVAILABLE),
				hasNoDueDate(),
				hasLocation("King 7th Floor", "ab7"),
				hasLocalBibId("65423515"),
				hasLocalItemType("999"),
				hasLocalItemTypeCode("999"),
				hasCanonicalItemType("UNKNOWN - Item has no owning context"),
				hasHoldCount(2),
				hasNoAgency(),
				hasNoHostLmsCode(),
				hasNoRawVolumeStatement(),
				hasNoParsedVolumeStatement(),
				isNotSuppressed(),
				isNotDeleted()
			)
		));
	}

	@Test
	void shouldProvideNoItemsWhenSierraRespondsWithNoRecordsFoundError() {
		sierraItemsAPIFixture.zeroItemsResponseForBibId("87878325");

		final var client = hostLmsFixture.createClient(CATALOGUING_HOST_LMS_CODE);

		final var items = getItems(client,"87878325");

		assertThat("Should have no items", items, is(empty()));
	}

	@Test
	void shouldProvideNoItemsWhenSierraRespondsWithNoItems() {
		final var sourceRecordId = "1274376";

		sierraItemsAPIFixture.itemsForBibId(sourceRecordId, emptyList());

		final var client = hostLmsFixture.createClient(CATALOGUING_HOST_LMS_CODE);

		final var items = getItems(client, sourceRecordId);

		assertThat("Should have no items", items, is(empty()));
	}

	@Test
	void shouldFailWhenCannotAuthenticateWithSierra() {
		// Arrange
		final var invalidAuthBaseUrl = "http://invalid-auth-sierra-test";

		hostLmsFixture.createSierraHostLms("bad-config-sierra-host-lms",
			"invalid-key", "invalid-secret", invalidAuthBaseUrl, "item");

		sierraItemsAPIFixture.zeroItemsResponseForBibId("7225825");

		// Act
		final var client = hostLmsFixture.createClient("bad-config-sierra-host-lms");

		final var problem = assertThrows(ThrowableProblem.class,
			() -> getItems(client, "7225825"));

		// Assert
		assertThat(problem, allOf(
			hasMessageForRequest("POST", "/iii/sierra-api/v6/token"),
			hasResponseStatusCode(401),
			hasNoResponseBody(),
			hasRequestMethod("POST")
		));
	}

	private static List<Item> getItems(HostLmsClient client, String sourceRecordId) {
		return singleValueFrom(client.getItems(BibRecord.builder()
			.sourceRecordId(sourceRecordId)
			.build()));
	}
}
