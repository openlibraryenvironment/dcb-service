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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockserver.client.MockServerClient;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.uri.UriBuilder;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.inject.Inject;
import lombok.Builder;
import lombok.Data;
import reactor.core.publisher.Mono;
import services.k_int.test.mockserver.MockServerMicronautTest;

@MockServerMicronautTest
@TestInstance(PER_CLASS)
class ConsortialFolioHostLmsClientItemTests {
	@Inject
	private HttpClient client;

	@Test
	void shouldBeAbleToFetchHoldings(MockServerClient mockServerClient) {
		// Arrange
		mockServerClient
			.when(org.mockserver.model.HttpRequest.request()
				.withHeader("Accept", APPLICATION_JSON)
				.withHeader("Host", "fake-folio")
				.withHeader("Authorization", "eyJzIjoic2FsdCIsInQiOiJ0ZW5hbnQiLCJ1IjoidXNlciJ9")
				.withQueryStringParameter("fullPeriodicals", "true")
				.withQueryStringParameter("instanceIds", "d68dfc67-a947-4b7a-9833-b71155d67579")
				.withPath("/rtac")
			)
			.respond(response()
				.withStatusCode(200)
				.withBody(json(OuterHoldings.builder()
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
					.build()))
			);

		// Act
		final var response = getHoldings().block();

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

	private Mono<OuterHoldings> getHoldings() {
		final var uri = UriBuilder.of("https://fake-folio/rtac")
			.queryParam("instanceIds", "d68dfc67-a947-4b7a-9833-b71155d67579")
			.queryParam("fullPeriodicals", true)
			.build();

		final var request = HttpRequest.create(HttpMethod.GET, uri.toString())
			// Base 64 encoded API key
			.header("Authorization", "eyJzIjoic2FsdCIsInQiOiJ0ZW5hbnQiLCJ1IjoidXNlciJ9")
			.accept(APPLICATION_JSON);

		return Mono.from(client.retrieve(request, Argument.of(OuterHoldings.class)));
	}

	@Serdeable
	@Builder
	@Data
	public static class OuterHoldings {
		@Nullable List<OuterHolding> holdings;
	}

	@Serdeable
	@Builder
	@Data
	public static class OuterHolding {
		@Nullable String instanceId;
		@Nullable List<Holding> holdings;
	}

	@Serdeable
	@Builder
	@Data
	public static class Holding {
		@Nullable String id;
		@Nullable String callNumber;
		@Nullable String location;
		@Nullable String status;
		@Nullable String permanentLoanType;
	}
}
