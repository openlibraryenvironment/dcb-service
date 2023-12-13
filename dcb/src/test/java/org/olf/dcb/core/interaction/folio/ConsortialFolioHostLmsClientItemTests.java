package org.olf.dcb.core.interaction.folio;

import static java.util.Collections.emptyList;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.olf.dcb.core.model.ItemStatusCode.AVAILABLE;
import static org.olf.dcb.core.model.ItemStatusCode.CHECKED_OUT;
import static org.olf.dcb.core.model.ItemStatusCode.UNAVAILABLE;
import static org.olf.dcb.test.matchers.ItemMatchers.hasBarcode;
import static org.olf.dcb.test.matchers.ItemMatchers.hasCallNumber;
import static org.olf.dcb.test.matchers.ItemMatchers.hasDueDate;
import static org.olf.dcb.test.matchers.ItemMatchers.hasHoldCount;
import static org.olf.dcb.test.matchers.ItemMatchers.hasHostLmsCode;
import static org.olf.dcb.test.matchers.ItemMatchers.hasLocalBibId;
import static org.olf.dcb.test.matchers.ItemMatchers.hasLocalId;
import static org.olf.dcb.test.matchers.ItemMatchers.hasLocalItemType;
import static org.olf.dcb.test.matchers.ItemMatchers.hasLocalItemTypeCode;
import static org.olf.dcb.test.matchers.ItemMatchers.hasLocation;
import static org.olf.dcb.test.matchers.ItemMatchers.hasNoBarcode;
import static org.olf.dcb.test.matchers.ItemMatchers.hasNoDueDate;
import static org.olf.dcb.test.matchers.ItemMatchers.hasNoLocalItemType;
import static org.olf.dcb.test.matchers.ItemMatchers.hasNoLocalItemTypeCode;
import static org.olf.dcb.test.matchers.ItemMatchers.hasStatus;
import static org.olf.dcb.test.matchers.ItemMatchers.isNotDeleted;
import static org.olf.dcb.test.matchers.ItemMatchers.isNotSuppressed;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockserver.client.MockServerClient;
import org.olf.dcb.core.interaction.FailedToGetItemsException;
import org.olf.dcb.core.interaction.HostLmsClient;
import org.olf.dcb.core.model.BibRecord;
import org.olf.dcb.core.model.Item;
import org.olf.dcb.core.model.ItemStatusCode;
import org.olf.dcb.test.AgencyFixture;
import org.olf.dcb.test.HostLmsFixture;
import org.olf.dcb.test.ReferenceValueMappingFixture;

import io.micronaut.core.annotation.Nullable;
import jakarta.inject.Inject;
import services.k_int.test.mockserver.MockServerMicronautTest;

@MockServerMicronautTest
class ConsortialFolioHostLmsClientItemTests {
	private static final String HOST_LMS_CODE = "folio-lms-client-item-tests";

	@Inject
	private HostLmsFixture hostLmsFixture;
	@Inject
	private ReferenceValueMappingFixture referenceValueMappingFixture;
	@Inject
	private AgencyFixture agencyFixture;

	private MockFolioFixture mockFolioFixture;
	private HostLmsClient client;

	@BeforeEach
	public void beforeEach(MockServerClient mockServerClient) {
		final var API_KEY = "eyJzIjoic2FsdCIsInQiOiJ0ZW5hbnQiLCJ1IjoidXNlciJ9";

		hostLmsFixture.deleteAll();
		referenceValueMappingFixture.deleteAll();
		agencyFixture.deleteAll();

		hostLmsFixture.createFolioHostLms(HOST_LMS_CODE, "https://fake-folio",
			API_KEY, "", "");

		mockFolioFixture = new MockFolioFixture(mockServerClient, "fake-folio", API_KEY);

		client = hostLmsFixture.createClient(HOST_LMS_CODE);
	}

	@Test
	void shouldBeAbleToGetItems() {
		// Arrange
		final var instanceId = UUID.randomUUID().toString();

		final var dueDate = Instant.parse("2023-12-11T23:59:59.000+00:00");

		mockFolioFixture.mockHoldingsByInstanceId(instanceId,
			Holding.builder()
				.id("ed26adb1-2e23-4aa6-a8cc-2f9892b10cf2")
				.callNumber("QA273.A5450 1984")
				.location("Crerar, Lower Level, Bookstacks")
				.locationCode("CLLA")
				.status("Available")
				.totalHoldRequests(1)
				.permanentLoanType("stks")
				.materialType(MaterialType.builder()
					.name("book")
					.build())
				.build(),
			Holding.builder()
				.id("eee7ded7-28cd-4a1d-9bbf-9e155cbe60b3")
				.barcode("26928683")
				.callNumber("QA273.A5450 1984")
				.location("Social Service Administration")
				.locationCode("SSA")
				.status("Checked out")
				.totalHoldRequests(2)
				.dueDate(dueDate)
				.permanentLoanType("stks")
				.build()
		);

		// Act
		final var items = getItems(instanceId);

		// Assert
		assertThat("Should have 2 items", items, hasSize(2));

		assertThat("Items should have expected properties", items,
			contains(
				allOf(
					hasLocalId("ed26adb1-2e23-4aa6-a8cc-2f9892b10cf2"),
					hasLocalBibId(instanceId),
					hasNoBarcode(),
					hasCallNumber("QA273.A5450 1984"),
					hasStatus(AVAILABLE),
					hasNoDueDate(),
					hasHoldCount(1),
					hasLocalItemType("book"),
					hasLocalItemTypeCode("book"),
					hasLocation("Crerar, Lower Level, Bookstacks", "CLLA"),
					isNotSuppressed(),
					isNotDeleted(),
					hasHostLmsCode(HOST_LMS_CODE)
				),
				allOf(
					hasLocalId("eee7ded7-28cd-4a1d-9bbf-9e155cbe60b3"),
					hasLocalBibId(instanceId),
					hasBarcode("26928683"),
					hasCallNumber("QA273.A5450 1984"),
					hasStatus(CHECKED_OUT),
					hasDueDate(dueDate),
					hasHoldCount(2),
					hasNoLocalItemType(),
					hasNoLocalItemTypeCode(),
					hasLocation("Social Service Administration", "SSA"),
					isNotSuppressed(),
					isNotDeleted(),
					hasHostLmsCode(HOST_LMS_CODE)
				)
			));
	}

	@Test
	void shouldMapItemStatusUsingReferenceValueMappings() {
		// Arrange
		final var instanceId = UUID.randomUUID().toString();

		final var checkedOutItemId = UUID.randomUUID().toString();
		final var availableItemId = UUID.randomUUID().toString();

		mockFolioFixture.mockHoldingsByInstanceId(instanceId,
			exampleHolding()
				.id(checkedOutItemId)
				.status("Checked out")
				.build(),
			exampleHolding()
				.id(availableItemId)
				.status("Available")
				.build()
		);

		// These mappings need to be unrealistic in order to distinguish between
		// use of the mappings over the fallback method
		mapStatus("Available", CHECKED_OUT);
		mapStatus("Checked out", UNAVAILABLE);

		// Act
		final var items = getItems(instanceId);

		// Assert
		assertThat("Items should have mapped status", items,
			contains(
				allOf(
					hasLocalId(checkedOutItemId),
					hasStatus(UNAVAILABLE)
				),
				allOf(
					hasLocalId(availableItemId),
					hasStatus(CHECKED_OUT)
				)
			));
	}

	@Test
	void shouldMapItemStatusUsingFallbackMappings() {
		// Arrange
		final var instanceId = UUID.randomUUID().toString();

		final var availableItemId = UUID.randomUUID().toString();
		final var checkedOutItemId = UUID.randomUUID().toString();
		final var declaredLostItemId = UUID.randomUUID().toString();

		mockFolioFixture.mockHoldingsByInstanceId(instanceId,
			exampleHolding()
				.id(availableItemId)
				.status("Available")
				.build(),
			exampleHolding()
				.id(checkedOutItemId)
				.status("Checked out")
				.build(),
			exampleHolding()
				.id(declaredLostItemId)
				.status("Declared lost")
				.build()
		);

		// Act
		final var items = getItems(instanceId);

		// Assert
		assertThat("Items should have mapped status", items,
			containsInAnyOrder(
				allOf(
					hasLocalId(availableItemId),
					hasStatus(AVAILABLE)
				),
				allOf(
					hasLocalId(checkedOutItemId),
					hasStatus(CHECKED_OUT)
				),
				allOf(
					hasLocalId(declaredLostItemId),
					hasStatus(UNAVAILABLE)
				)
			));
	}

	@Test
	void shouldBeAbleToHandleZeroInnerHoldings() {
		// Arrange
		final var instanceId = UUID.randomUUID().toString();

		mockFolioFixture.mockHoldingsByInstanceId(instanceId, emptyList());

		// Act
		final var items = getItems(instanceId);

		// Assert
		assertThat("Should have zero items", items, hasSize(0));
	}

	@Test
	void shouldFailWhenLikelyInvalidApiKeyResponseIsReceived() {
		// Arrange
		final var instanceId = UUID.randomUUID().toString();
		
		// An invalid API key results in a response with no outer holdings
		mockFolioFixture.mockHoldingsByInstanceId(instanceId, OuterHoldings.builder()
			.holdings(emptyList())
			.build());

		// Act
		final var exception = assertThrows(LikelyInvalidApiKeyException.class,
			() -> getItems(instanceId));

		// Assert
		assertThat("Error should not be null", exception, is(notNullValue()));
		assertThat(exception, hasProperty("message",
			is("No errors or outer holdings (instances) returned from RTAC for instance ID: \""
				+ instanceId + "\". Likely caused by invalid API key")));
	}

	@Test
	void shouldFailWhenInstanceNotFoundErrorReceived() {
		// Arrange
		final var instanceId = UUID.randomUUID().toString();

		mockFolioFixture.mockHoldingsByInstanceId(instanceId, OuterHoldings.builder()
			.errors(List.of(
				RtacError.builder()
					.code("404")
					.message("Instance " + instanceId + " can not be retrieved")
				.build()))
			.build());

		// Act
		final var exception = assertThrows(FailedToGetItemsException.class,
			() -> getItems(instanceId));

		// Assert
		assertThat("Error should not be null", exception, is(notNullValue()));
		assertThat(exception, hasProperty("localBibId", is(instanceId)));
	}

	@Test
	void shouldReportZeroItemsWhenHoldingsCannotBeFound() {
		// Arrange
		final var instanceId = UUID.randomUUID().toString();

		mockFolioFixture.mockHoldingsByInstanceId(instanceId, OuterHoldings.builder()
			.errors(List.of(
				RtacError.builder()
					.code("404")
					.message("Holdings not found for instance " + instanceId)
					.build()))
			.build());

		// Act
		final var items = getItems(instanceId);

		// Assert
		assertThat("Should have zero items", items, empty());
	}

	@Test
	void shouldFailWhenMultipleOuterHoldingsAreReceived() {
		// DCB only asks for holdings for a single instance at a time
		// RTAC should never respond with multiple outer holdings (instances)

		// Arrange
		final var requestedInstanceId = UUID.randomUUID().toString();
		final var otherInstanceId = UUID.randomUUID().toString();

		mockFolioFixture.mockHoldingsByInstanceId(requestedInstanceId, OuterHoldings.builder()
			.holdings(List.of(
				OuterHolding.builder()
					.instanceId(requestedInstanceId)
					.holdings(List.of(exampleHolding().build()))
					.build(),
				OuterHolding.builder()
					.instanceId(otherInstanceId)
					.holdings(List.of(exampleHolding().build()))
					.build()
			))
			.build());

		// Act
		final var exception = assertThrows(UnexpectedOuterHoldingException.class,
			() -> getItems(requestedInstanceId));

		// Assert
		assertThat("Error should not be null", exception, is(notNullValue()));
		assertThat(exception, hasProperty("localBibId", is(requestedInstanceId)));
	}

	@Test
	void shouldIgnoreHoldingStatusThatDoesNotMatchFolioItemStatus() {
		// When RTAC encounters a holdings record without any items
		// it includes the holdings record in the response
		// DCB is only interested in items and thus needs to differentiate between
		// holdings in the response that are items and those that are only holdings
		// For more information on the flow inside RTAC - https://github.com/folio-org/mod-rtac/blob/3e7f25445ff79b60690fa2025f3a426d9e57fd21/src/main/java/org/folio/mappers/FolioToRtacMapper.java#L112
		
		// Arrange
		final var instanceId = UUID.randomUUID().toString();

		final var folioItemStatuses = List.of(
			"Aged to lost",
			"Available",
			"Awaiting pickup",
			"Awaiting delivery",
			"Checked out",
			"Claimed returned",
			"Declared lost",
			"In process",
			"In process (non-requestable)",
			"In transit",
			"Intellectual item",
			"Long missing",
			"Lost and paid",
			"Missing",
			"On order",
			"Paged",
			"Restricted",
			"Order closed",
			"Unavailable",
			"Unknown",
			"Withdrawn");

		final var holdingsRecordWithoutStatementStatus = "Multi";
		final var holdingsRecordWithStatementStatus = "Some holdings statement";

		final var allExampleHoldings = new ArrayList<Holding>();

		folioItemStatuses.forEach(folioItemStatus ->
			allExampleHoldings.add(exampleHolding().status(folioItemStatus).build()));

		allExampleHoldings.add(exampleHolding().status(holdingsRecordWithoutStatementStatus).build());

		allExampleHoldings.add(exampleHolding().status(holdingsRecordWithStatementStatus).build());

		mockFolioFixture.mockHoldingsByInstanceId(instanceId, allExampleHoldings);

		// Act
		final var items = getItems(instanceId);

		// Assert

		// As this is based upon the count, this could be a brittle check
		assertThat("Should only include items based upon holdings with FOLIO item status",
			items, hasSize(folioItemStatuses.size()));
	}

	@Test
	void shouldDefineAvailableSettings() {
		final var settings = client.getSettings();

		assertThat("Should have expected settings", settings, containsInAnyOrder(
			allOf(
				hasProperty("name", is("base-url")),
				hasProperty("description", is("Base URL of the FOLIO system")),
				hasProperty("mandatory", is(true)),
				hasProperty("typeCode", is("URL"))
			),
			allOf(
				hasProperty("name", is("apikey")),
				hasProperty("description", is("API key for this FOLIO tenant")),
				hasProperty("mandatory", is(true)),
				hasProperty("typeCode", is("String"))
			)
		));
	}

	@Nullable
	private List<Item> getItems(String instanceId) {
		return client.getItems(BibRecord.builder()
				.sourceRecordId(instanceId)
				.build())
			.block();
	}

	private void mapStatus(String localStatus, ItemStatusCode canonicalStatus) {
		referenceValueMappingFixture
			.defineItemStatusMapping(HOST_LMS_CODE, localStatus, canonicalStatus.name());
	}

	private static Holding.HoldingBuilder exampleHolding() {
		return Holding.builder()
			.callNumber("QA273.A5450 1984")
			.location("Crerar, Lower Level, Bookstacks")
			.status("Available");
	}
}
