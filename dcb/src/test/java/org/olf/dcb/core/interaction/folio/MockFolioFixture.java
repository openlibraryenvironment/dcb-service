package org.olf.dcb.core.interaction.folio;

import static org.olf.dcb.test.MockServerCommonResponses.created;
import static org.olf.dcb.test.MockServerCommonResponses.noContent;
import static org.olf.dcb.test.MockServerCommonResponses.ok;
import static org.olf.dcb.test.MockServerCommonResponses.okJson;

import java.util.List;

import org.mockserver.client.MockServerClient;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.olf.dcb.test.MockServer;
import org.olf.dcb.test.MockServerCommonRequests;

import io.micronaut.serde.annotation.Serdeable;
import lombok.Builder;
import lombok.Value;

public class MockFolioFixture {
	private final MockServer mockServer;
	private final MockServerCommonRequests commonRequests;

	public MockFolioFixture(MockServerClient mockServerClient, String host, String apiKey) {
		this.commonRequests = new MockServerCommonRequests(host, apiKey);
		this.mockServer = new MockServer(mockServerClient, commonRequests);
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
		mockHoldingsByInstanceId(instanceId, okJson(holdings));
	}

	void mockHoldingsByInstanceId(String instanceId, HttpResponse response) {
		mockServer.mock(commonRequests.get("/rtac")
			.withQueryStringParameter("fullPeriodicals", "true")
			.withQueryStringParameter("instanceIds", instanceId), response);
	}

	public void mockPatronPinVerify() {
		mockServer.mockPost("/users/patron-pin/verify", ok());
	}

	public void mockGetUsersWithQuery(String queryField, String queryValue, User... users) {
		mockGetUsersWithQuery(queryField, queryValue, okJson(UserCollection.builder().users(List.of(users)).build()));
	}

	public void mockGetUsersWithQuery(String queryField, String queryValue, HttpResponse response) {
		mockServer.mockGet("/users/users", "query", queryField + "==\"" + queryValue + "\"", response);
	}

	void mockCreateTransaction(CreateTransactionResponse response) {
		mockCreateTransaction(created(response));
	}

	void mockCreateTransaction(HttpResponse response) {
		mockServer.replaceMock(commonRequests.post(createTransactionPath()), response);
	}

	void verifyCreateTransaction(CreateTransactionRequest body) {
		mockServer.verifyPost(createTransactionPath(), body);
	}

	public void mockGetTransactionStatus(String transactionId, String status) {
		mockGetTransactionStatus(transactionId, okJson(TransactionStatus.builder().status(status).build()));
	}

	public void mockGetTransactionStatus(String transactionId, HttpResponse response) {
		mockServer.mockGet(getTransactionStatusPath(transactionId), response);
	}

	public void mockUpdateTransaction(String transactionId) {
		mockUpdateTransaction(transactionId, noContent());
	}

	public void mockUpdateTransaction(String transactionId, HttpResponse response) {
		mockServer.mockPut(updateTransactionPath(transactionId), response);
	}

	public void verifyUpdateTransaction(String transactionId, UpdateTransactionRequest expectedRequest) {
		mockServer.verifyPut(updateTransactionPath(transactionId), expectedRequest);
	}

	public void verifyNoUpdateTransaction(String transactionId) {
		mockServer.verifyPutNever(updateTransactionPath(transactionId));
	}

	void mockRenewTransaction(String transactionId, Object responseBody) {
		mockServer.mockPut(renewTransactionPath(transactionId), responseBody);
	}

	void mockRenewTransaction(String transactionId, HttpResponse response) {
		mockServer.mock(renewTransactionRequest(transactionId), response);
	}

	public void verifyRenewTransaction(String transactionId) {
		mockServer.verifyPut(renewTransactionPath(transactionId));
	}

	private HttpRequest renewTransactionRequest(String transactionId) {
		return commonRequests.put(renewTransactionPath(transactionId));
	}

	private static String createTransactionPath() {
		// This has to be unspecific as the transaction ID is generated internally
		return "/dcbService/transactions/.*";
	}

	private static String getTransactionStatusPath(String transactionId) {
		return "/dcbService/transactions/%s/status".formatted(transactionId);
	}

	private static String updateTransactionPath(String transactionId) {
		return "/dcbService/transactions/%s".formatted(transactionId);
	}

	private static String renewTransactionPath(String transactionId) {
		return "/dcbService/transactions/%s/renew".formatted(transactionId);
	}

	@Serdeable
	@Builder
	@Value
	public static class ErrorResponse {
		Integer code;
		String errorMessage;
	}
}
