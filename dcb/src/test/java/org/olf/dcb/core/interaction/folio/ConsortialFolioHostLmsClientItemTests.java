package org.olf.dcb.core.interaction.folio;

import static io.micronaut.http.MediaType.APPLICATION_JSON;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.JsonBody.json;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockserver.client.MockServerClient;
import org.olf.dcb.test.HostLmsFixture;

import io.micronaut.http.client.HttpClient;
import jakarta.inject.Inject;
import services.k_int.test.mockserver.MockServerMicronautTest;

@MockServerMicronautTest
@TestInstance(PER_CLASS)
class ConsortialFolioHostLmsClientItemTests {
	private static final String HOST_LMS_CODE = "folio-lms-client-item-tests";

	@Inject
	private HostLmsFixture hostLmsFixture;
	@Inject
	private HttpClient httpClient;

	@BeforeEach
	public void beforeEach() {
		hostLmsFixture.deleteAll();

		hostLmsFixture.createFolioHostLms(HOST_LMS_CODE, "", "", "", "");
	}

	@Test
	void shouldBeAbleToFetchHoldings(MockServerClient mockServerClient) {
		// Arrange
		mockHoldingsByInstanceId(mockServerClient, "d68dfc67-a947-4b7a-9833-b71155d67579", OuterHoldings.builder()
			.holdings(List.of(
				OuterHolding.builder()
					.instanceId("d68dfc67-a947-4b7a-9833-b71155d67579")
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

		final var client = hostLmsFixture.createFolioClient(HOST_LMS_CODE);

		// Act
		final var response = client.getHoldings(httpClient).block();

		// Assert
		assertThat("Response should not be null", response, is(notNullValue()));
		assertThat("Should have 1 outer holdings", response.getHoldings(), hasSize(1));

		final var onlyOuterHolding = response.getHoldings().get(0);

		assertThat("Should have instance ID",
			onlyOuterHolding.getInstanceId(), is("d68dfc67-a947-4b7a-9833-b71155d67579"));

		assertThat("Should have 2 holdings", onlyOuterHolding.getHoldings(), hasSize(2));

		assertThat("Holdings should have expected properties", onlyOuterHolding.getHoldings(),
			contains(
				allOf(
					hasProperty("id", is("ed26adb1-2e23-4aa6-a8cc-2f9892b10cf2")),
					hasProperty("callNumber", is("QA273.A5450 1984")),
					hasProperty("location", is("Crerar, Lower Level, Bookstacks")),
					hasProperty("status", is("Available")),
					hasProperty("permanentLoanType", is("stks"))
				),
				allOf(
					hasProperty("id", is("eee7ded7-28cd-4a1d-9bbf-9e155cbe60b3")),
					hasProperty("callNumber", is("QA273.A5450 1984")),
					hasProperty("location", is("Social Service Administration")),
					hasProperty("status", is("Available")),
					hasProperty("permanentLoanType", is("stks"))
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
