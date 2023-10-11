package org.olf.dcb.core.interaction.folio;

import static io.micronaut.http.MediaType.APPLICATION_JSON;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.JsonBody.json;

import org.mockserver.client.MockServerClient;

public class MockFolioFixture {
	private final MockServerClient mockServerClient;
	private final String host;
	private final String apiKey;

	public MockFolioFixture(MockServerClient mockServerClient, String host, String apiKey) {
		this.mockServerClient = mockServerClient;
		this.host = host;
		this.apiKey = apiKey;
	}

	void mockHoldingsByInstanceId(String instanceId, OuterHoldings holdings) {
		mockServerClient
			.when(org.mockserver.model.HttpRequest.request()
				.withHeader("Accept", APPLICATION_JSON)
				.withHeader("Host", host)
				.withHeader("Authorization", apiKey)
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
