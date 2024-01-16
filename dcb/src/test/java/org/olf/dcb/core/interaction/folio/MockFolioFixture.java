package org.olf.dcb.core.interaction.folio;

import static io.micronaut.http.MediaType.APPLICATION_JSON;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.JsonBody.json;
import static org.mockserver.verify.VerificationTimes.once;

import java.util.List;

import org.mockserver.client.MockServerClient;
import org.mockserver.model.HttpRequest;
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
			.when(request()
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
		mockGetUsersWithQuery("barcode", barcode, user);

		mockPatronVerify(response()
			.withStatusCode(200));
	}

	private void mockPatronVerify(HttpResponse response) {
		mockServerClient
			.when(request()
				.withHeader("Host", host)
				.withHeader("Authorization", apiKey)
				.withHeader("Accept", APPLICATION_JSON)
				.withPath("/users/patron-pin/verify"))
			.respond(response);
	}

	public void mockGetUsersWithQuery(String queryField, String queryValue, User... users) {
		mockServerClient
			.when(request()
				.withHeader("Host", host)
				.withHeader("Authorization", apiKey)
				.withHeader("Accept", APPLICATION_JSON)
				.withPath("/users/users")
				.withQueryStringParameter("query", queryField + "==\"" + queryValue + "\""))
			.respond(response()
				.withStatusCode(200)
				.withBody(json(
					UserCollection.builder()
						.users(List.of(users))
						.build())));
	}

	public void mockGetUsersWithQuery(String queryField, String queryValue, HttpResponse httpResponse) {
		mockServerClient
			.when(request()
				.withHeader("Host", host)
				.withHeader("Authorization", apiKey)
				.withHeader("Accept", APPLICATION_JSON)
				.withPath("/users/users")
				.withQueryStringParameter("query", queryField + "==\"" + queryValue + "\""))
			.respond(httpResponse);
	}

	void mockCreateTransaction(CreateTransactionResponse response) {
		mockServerClient
			.when(createTransactionRequest())
			.respond(response()
				.withStatusCode(201)
				.withBody(json(response)));
	}

	void verifyCreateTransaction(CreateTransactionRequest request) {
		mockServerClient.verify(createTransactionRequest()
			.withBody(json(request)), once());
	}

	private HttpRequest createTransactionRequest() {
		return request()
			.withMethod("POST")
			.withHeader("Host", host)
			.withHeader("Authorization", apiKey)
			.withHeader("Accept", APPLICATION_JSON)
			// This has to be unspecific as the transaction ID is generated internally
			.withPath("/dcbService/transactions/.*");
	}

	@Serdeable
	@Builder
	@Value
	public static class ErrorResponse {
		Integer code;
		String errorMessage;
	}
}
