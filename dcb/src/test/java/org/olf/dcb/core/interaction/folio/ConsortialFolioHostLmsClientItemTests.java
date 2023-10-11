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
import org.olf.dcb.core.model.BibRecord;
import org.olf.dcb.core.interaction.HostLmsClient;
import org.olf.dcb.test.HostLmsFixture;

import jakarta.inject.Inject;
import services.k_int.test.mockserver.MockServerMicronautTest;

@MockServerMicronautTest
@TestInstance(PER_CLASS)
class ConsortialFolioHostLmsClientItemTests {
	@Inject
	private HostLmsFixture hostLmsFixture;
	private MockFolioFixture mockFolioFixture;
	private HostLmsClient client;

	@BeforeEach
	public void beforeEach(MockServerClient mockServerClient) {
		final var API_KEY = "eyJzIjoic2FsdCIsInQiOiJ0ZW5hbnQiLCJ1IjoidXNlciJ9";
		final var HOST_LMS_CODE = "folio-lms-client-item-tests";

		hostLmsFixture.deleteAll();

		hostLmsFixture.createFolioHostLms(HOST_LMS_CODE, "https://fake-folio",
			API_KEY, "", "");

		mockFolioFixture = new MockFolioFixture(mockServerClient, "fake-folio", API_KEY);

		client = hostLmsFixture.createClient(HOST_LMS_CODE);
	}

	@Test
	void shouldBeAbleToGetItems() {
		// Arrange
		final var instanceId = UUID.randomUUID().toString();

		mockFolioFixture.mockHoldingsByInstanceId(instanceId, OuterHoldings.builder()
			.holdings(List.of(
				OuterHolding.builder()
					.instanceId(instanceId)
					.holdings(List.of(
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
					))
					.build()
			))
			.build());

		// Act
		final var items = client.getItems(BibRecord.builder()
				.sourceRecordId(instanceId)
				.build())
			.block();

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
	void shouldDefineAvailableSettings() {
		final var settings = client.getSettings();

		assertThat("Should have expected settings", settings, containsInAnyOrder(
			allOf(
				hasProperty("name", is("base-url")),
				hasProperty("description", is("Base URL Of FOLIO System")),
				hasProperty("mandatory", is(true)),
				hasProperty("typeCode", is("URL"))
			)
		));
	}
}
