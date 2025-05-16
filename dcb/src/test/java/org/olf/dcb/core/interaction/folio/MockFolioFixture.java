package org.olf.dcb.core.interaction.folio;

import static io.micronaut.http.MediaType.APPLICATION_JSON;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.JsonBody.json;
import static org.mockserver.verify.VerificationTimes.never;
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
		mockHoldingsByInstanceId(instanceId, response()
			.withStatusCode(200)
			.withBody(json));
	}

	void mockHoldingsByInstanceId(String instanceId, HttpResponse response) {
		mockServerClient
			.when(authorizedRequest("GET")
				.withPath("/rtac")
				.withQueryStringParameter("fullPeriodicals", "true")
				.withQueryStringParameter("instanceIds", instanceId))
			.respond(response);
	}

	void mockPatronAuth(String barcode, User user) {
		mockGetUsersWithQuery("barcode", barcode, user);

		mockPatronVerify(response()
			.withStatusCode(200));
	}

	private void mockPatronVerify(HttpResponse response) {
		mockServerClient
			.when(authorizedRequest("POST")
				.withPath("/users/patron-pin/verify"))
			.respond(response);
	}

	public void mockGetUsersWithQuery(String queryField, String queryValue, User... users) {
		mockServerClient
			.when(authorizedRequest("GET")
				.withPath("/users/users")
				.withQueryStringParameter("query", queryField + "==\"" + queryValue + "\""))
			.respond(response()
				.withStatusCode(200)
				.withBody(json(
					UserCollection.builder()
						.users(List.of(users))
						.build())));
	}

	public void mockGetUsersWithQuery(String queryField, String queryValue, HttpResponse response) {
		mockServerClient
			.when(authorizedRequest("GET")
				.withPath("/users/users")
				.withQueryStringParameter("query", queryField + "==\"" + queryValue + "\""))
			.respond(response);
	}

	void mockCreateTransaction(CreateTransactionResponse response) {
		mockCreateTransaction(response()
			.withStatusCode(201)
			.withBody(json(response)));
	}

	void mockCreateTransaction(HttpResponse response) {
		// Have to remove previous expectations as there is no way to match specifically
		mockServerClient.clear(createTransactionRequest());

		mockServerClient
			.when(createTransactionRequest())
			.respond(response);
	}

	void verifyCreateTransaction(CreateTransactionRequest request) {
		mockServerClient.verify(createTransactionRequest()
			.withBody(json(request)), once());
	}

	private HttpRequest createTransactionRequest() {
		return authorizedRequest("POST")
			// This has to be unspecific as the transaction ID is generated internally
			.withPath("/dcbService/transactions/.*");
	}

	public void mockGetTransactionStatus(String transactionId, String status) {
		mockGetTransactionStatus(transactionId, response()
			.withStatusCode(200)
			.withBody(json(TransactionStatus.builder()
				.status(status)
				.build())));
	}

	public void mockGetTransactionStatus(String transactionId, HttpResponse response) {
		mockServerClient
			.when(authorizedRequest("GET")
				.withPath("/dcbService/transactions/%s/status".formatted(transactionId)))
			.respond(response);
	}

	public void mockUpdateTransaction(String transactionId) {
		mockUpdateTransaction(transactionId, response().withStatusCode(204));
	}

	public void mockUpdateTransaction(String transactionId, HttpResponse response) {
		mockServerClient.when(updateTransactionRequest(transactionId))
			.respond(response);
	}

	public void verifyUpdateTransaction(String transactionId,
		UpdateTransactionRequest expectedRequest) {

		mockServerClient.verify(updateTransactionRequest(transactionId)
			.withBody(json(expectedRequest)), once());
	}

	public void verifyNoUpdateTransaction(String transactionId) {
		mockServerClient.verify(updateTransactionRequest(transactionId), never());
	}

	private HttpRequest updateTransactionRequest(String transactionId) {
		return authorizedRequest("PUT")
			.withPath("/dcbService/transactions/%s".formatted(transactionId));
	}

	void mockRenewTransaction(String transactionId, HttpResponse response) {
		mockServerClient
			.when(renewTransactionRequest(transactionId))
			.respond(response);
	}

	public void verifyRenewTransaction(String transactionId) {
		mockServerClient.verify(renewTransactionRequest(transactionId), once());
	}

	private HttpRequest renewTransactionRequest(String transactionId) {
		return authorizedRequest("PUT")
			.withPath("/dcbService/transactions/%s/renew".formatted(transactionId));
	}


	private HttpRequest authorizedRequest(String method) {
		return request()
			.withMethod(method)
			.withHeader("Host", host)
			.withHeader("Authorization", apiKey)
			.withHeader("Accept", APPLICATION_JSON);
	}

	@Serdeable
	@Builder
	@Value
	public static class ErrorResponse {
		Integer code;
		String errorMessage;
	}
}
