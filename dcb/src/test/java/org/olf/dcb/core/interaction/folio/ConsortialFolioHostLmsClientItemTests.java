package org.olf.dcb.core.interaction.folio;

import static io.micronaut.http.MediaType.APPLICATION_JSON;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.JsonBody.json;
import static org.olf.dcb.core.model.ItemStatusCode.AVAILABLE;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockserver.client.MockServerClient;
import org.olf.dcb.core.model.BibRecord;
import org.olf.dcb.test.HostLmsFixture;

import jakarta.inject.Inject;
import services.k_int.test.mockserver.MockServerMicronautTest;

@MockServerMicronautTest
@TestInstance(PER_CLASS)
class ConsortialFolioHostLmsClientItemTests {
	private static final String HOST_LMS_CODE = "folio-lms-client-item-tests";

	@Inject
	private HostLmsFixture hostLmsFixture;

	@BeforeEach
	public void beforeEach() {
		hostLmsFixture.deleteAll();

		hostLmsFixture.createFolioHostLms(HOST_LMS_CODE, "", "", "", "");
	}

	@Test
	void shouldBeAbleToGetItems(MockServerClient mockServerClient) {
		// Arrange
		final var instanceId = UUID.randomUUID().toString();

		mockHoldingsByInstanceId(mockServerClient, instanceId, OuterHoldings.builder()
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

		final var client = hostLmsFixture.createClient(HOST_LMS_CODE);

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
					hasProperty("localId", is("ed26adb1-2e23-4aa6-a8cc-2f9892b10cf2")),
					hasProperty("localBibId", is(instanceId)),
					hasProperty("callNumber", is("QA273.A5450 1984")),
					hasProperty("status",
						hasProperty("code", is(AVAILABLE))),
					hasProperty("localItemType", is("stks")),
					hasProperty("location",
						hasProperty("name", is("Crerar, Lower Level, Bookstacks")))
				),
				allOf(
					hasProperty("localId", is("eee7ded7-28cd-4a1d-9bbf-9e155cbe60b3")),
					hasProperty("localBibId", is(instanceId)),
					hasProperty("callNumber", is("QA273.A5450 1984")),
					hasProperty("status",
					hasProperty("code", is(AVAILABLE))),
					hasProperty("localItemType", is("stks")),
					hasProperty("location",
						hasProperty("name", is("Social Service Administration")))
				)
			));
	}

	private static void mockHoldingsByInstanceId(MockServerClient mockServerClient,
		String instanceId, OuterHoldings holdings) {

		mockServerClient
			.when(org.mockserver.model.HttpRequest.request()
				.withHeader("Accept", APPLICATION_JSON)
				.withHeader("Host", "fake-folio")
				.withHeader("Authorization", "eyJzIjoic2FsdCIsInQiOiJ0ZW5hbnQiLCJ1IjoidXNlciJ9")
				.withQueryStringParameter("fullPeriodicals", "true")
				.withQueryStringParameter("instanceIds", instanceId)
				.withPath("/rtac")
			)
			.respond(response()
				.withStatusCode(200)
				.withBody(json(holdings))
			);
	}
}
