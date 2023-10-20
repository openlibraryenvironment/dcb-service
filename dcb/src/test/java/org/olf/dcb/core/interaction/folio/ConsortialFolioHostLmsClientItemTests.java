package org.olf.dcb.core.interaction.folio;

import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.olf.dcb.core.model.ItemStatusCode.AVAILABLE;
import static org.olf.dcb.core.model.ItemStatusCode.CHECKED_OUT;
import static org.olf.dcb.core.model.ItemStatusCode.UNAVAILABLE;
import static org.olf.dcb.test.matchers.ItemMatchers.hasCallNumber;
import static org.olf.dcb.test.matchers.ItemMatchers.hasLocalBibId;
import static org.olf.dcb.test.matchers.ItemMatchers.hasLocalId;
import static org.olf.dcb.test.matchers.ItemMatchers.hasLocalItemType;
import static org.olf.dcb.test.matchers.ItemMatchers.hasLocation;
import static org.olf.dcb.test.matchers.ItemMatchers.hasStatus;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockserver.client.MockServerClient;
import org.olf.dcb.core.interaction.HostLmsClient;
import org.olf.dcb.core.model.BibRecord;
import org.olf.dcb.core.model.Item;
import org.olf.dcb.core.model.ItemStatusCode;
import org.olf.dcb.test.HostLmsFixture;
import org.olf.dcb.test.ReferenceValueMappingFixture;

import io.micronaut.core.annotation.Nullable;
import jakarta.inject.Inject;
import services.k_int.test.mockserver.MockServerMicronautTest;

@MockServerMicronautTest
@TestInstance(PER_CLASS)
class ConsortialFolioHostLmsClientItemTests {
	private static final String HOST_LMS_CODE = "folio-lms-client-item-tests";

	@Inject
	private HostLmsFixture hostLmsFixture;
	@Inject
	private ReferenceValueMappingFixture referenceValueMappingFixture;

	private MockFolioFixture mockFolioFixture;
	private HostLmsClient client;

	@BeforeEach
	public void beforeEach(MockServerClient mockServerClient) {
		final var API_KEY = "eyJzIjoic2FsdCIsInQiOiJ0ZW5hbnQiLCJ1IjoidXNlciJ9";

		hostLmsFixture.deleteAll();

		referenceValueMappingFixture.deleteAll();

		hostLmsFixture.createFolioHostLms(HOST_LMS_CODE, "https://fake-folio",
			API_KEY, "", "");

		mockFolioFixture = new MockFolioFixture(mockServerClient, "fake-folio", API_KEY);

		client = hostLmsFixture.createClient(HOST_LMS_CODE);
	}

	@Test
	void shouldBeAbleToGetItems() {
		// Arrange
		final var instanceId = UUID.randomUUID().toString();

		mockFolioFixture.mockHoldingsByInstanceId(instanceId,
			Holding.builder()
				.id("ed26adb1-2e23-4aa6-a8cc-2f9892b10cf2")
				.callNumber("QA273.A5450 1984")
				.location("Crerar, Lower Level, Bookstacks")
				.status("Available")
				.permanentLoanType("stks")
				.build(),
			Holding.builder()
				.id("eee7ded7-28cd-4a1d-9bbf-9e155cbe60b3")
				.callNumber("QA273.A5450 1984")
				.location("Social Service Administration")
				.status("Available")
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
					hasCallNumber("QA273.A5450 1984"),
					hasStatus(AVAILABLE),
					hasLocalItemType("stks"),
					hasLocation("Crerar, Lower Level, Bookstacks")
				),
				allOf(
					hasLocalId("eee7ded7-28cd-4a1d-9bbf-9e155cbe60b3"),
					hasLocalBibId(instanceId),
					hasCallNumber("QA273.A5450 1984"),
					hasStatus(AVAILABLE),
					hasLocalItemType("stks"),
					hasLocation("Social Service Administration")
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
