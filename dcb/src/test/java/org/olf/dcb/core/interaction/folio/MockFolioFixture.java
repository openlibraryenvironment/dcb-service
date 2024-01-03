package org.olf.dcb.core.interaction.folio;

import static io.micronaut.http.MediaType.APPLICATION_JSON;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.JsonBody.json;

import java.util.List;

import org.mockserver.client.MockServerClient;
import org.mockserver.model.JsonBody;

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
				.withHeader("Accept", APPLICATION_JSON)
				.withHeader("Host", host)
				.withHeader("Authorization", apiKey)
				.withQueryStringParameter("fullPeriodicals", "true")
				.withQueryStringParameter("instanceIds", instanceId)
				.withPath("/rtac"))
			.respond(response()
				.withStatusCode(200)
				.withBody(json));
	}

	void mockFindUserByBarcode(String barcode, String localId) {
		mockServerClient
			.when(org.mockserver.model.HttpRequest.request()
				.withHeader("Host", host)
				.withHeader("Authorization", apiKey)
				.withPath("/users/users")
				.withQueryStringParameter("query", "barcode==\"" + barcode + "\""))
			.respond(response()
				.withStatusCode(200)
				.withBody(json(
					UserCollection.builder()
						.users(List.of(
							User.builder()
								.id(localId)
								.build()))
					.build())));
	}
}
