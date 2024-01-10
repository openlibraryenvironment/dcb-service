package org.olf.dcb.core.interaction.folio;

import static io.micronaut.http.MediaType.APPLICATION_JSON;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.JsonBody.json;

import java.util.List;

import org.mockserver.client.MockServerClient;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.JsonBody;

import io.micronaut.serde.annotation.Serdeable;
import lombok.Builder;
import lombok.Value;

public class MockFolioFixture {
	private final MockServerClient mockServerClient;
	private final String host;
	private final String apiKey;

	public MockFolioFixture(MockServerClient mockServerClient, String host, String apiKey) {
		this.mockServerClient = mockServerClient;
		this.host = host;
		this.apiKey = apiKey;
	}

	void mockHoldingsByInstanceId(String instanceId, Holding... holdings) {
		mockHoldingsByInstanceId(instanceId, List.of(holdings));
	}

	void mockHoldingsByInstanceId(String instanceId, List<Holding> holdings) {
		mockHoldingsByInstanceId(instanceId, OuterHoldings.builder()
			.holdings(List.of(
				OuterHolding.builder()
					.instanceId(instanceId)
					.holdings(holdings)
					.build()
			))
			.build());
	}

	void mockHoldingsByInstanceId(String instanceId, OuterHoldings holdings) {
		mockHoldingsByInstanceId(instanceId, json(holdings));
	}

	void mockHoldingsByInstanceId(String instanceId, JsonBody json) {
		mockServerClient
			.when(org.mockserver.model.HttpRequest.request()
				.withHeader("Host", host)
				.withHeader("Authorization", apiKey)
				.withHeader("Accept", APPLICATION_JSON)
				.withPath("/rtac")
				.withQueryStringParameter("fullPeriodicals", "true")
				.withQueryStringParameter("instanceIds", instanceId))
			.respond(response()
				.withStatusCode(200)
				.withBody(json));
	}

	void mockPatronAuth(String barcode, User user) {
		mockFindUsersByBarcode(barcode, response()
			.withStatusCode(200)
			.withBody(json(
				UserCollection.builder()
					.users(List.of(user))
					.build())));

		mockPatronVerify(response()
			.withStatusCode(200));
	}

	private void mockPatronVerify(HttpResponse response) {
		mockServerClient
			.when(org.mockserver.model.HttpRequest.request()
				.withHeader("Host", host)
				.withHeader("Authorization", apiKey)
				.withHeader("Accept", APPLICATION_JSON)
				.withPath("/users/patron-pin/verify"))
			.respond(response);
	}

	void mockFindUsersByBarcode(String barcode, User... users) {
		mockFindUsersByBarcode(barcode, response()
			.withStatusCode(200)
			.withBody(json(
				UserCollection.builder()
					.users(List.of(users))
				.build())));
	}

	public void mockFindUsersByBarcode(String barcode, HttpResponse httpResponse) {
		mockServerClient
			.when(org.mockserver.model.HttpRequest.request()
				.withHeader("Host", host)
				.withHeader("Authorization", apiKey)
				.withHeader("Accept", APPLICATION_JSON)
				.withPath("/users/users")
				.withQueryStringParameter("query", "barcode==\"" + barcode + "\""))
			.respond(httpResponse);
	}

	public void mockGetPatronByUsername(String username, User... users) {
		mockServerClient
			.when(org.mockserver.model.HttpRequest.request()
				.withHeader("Host", host)
				.withHeader("Authorization", apiKey)
				.withHeader("Accept", APPLICATION_JSON)

				.withPath("/users/users")
				.withQueryStringParameter("query", "username==\"" + username + "\""))
			.respond(response()
				.withStatusCode(200)
				.withBody(json(
					UserCollection.builder()
						.users(List.of(users))
						.build())));
	}

	public void mockFindUsersById(String localId, User... users) {
		mockServerClient
			.when(org.mockserver.model.HttpRequest.request()
				.withHeader("Host", host)
				.withHeader("Authorization", apiKey)
				.withHeader("Accept", APPLICATION_JSON)

				.withPath("/users/users")
				.withQueryStringParameter("query", "id==\"" + localId + "\""))
			.respond(response()
				.withStatusCode(200)
				.withBody(json(
					UserCollection.builder()
						.users(List.of(users))
						.build())));
	}

    @Serdeable
	@Builder
	@Value
	public static class ErrorResponse {
		Integer code;
		String errorMessage;
	}
}
