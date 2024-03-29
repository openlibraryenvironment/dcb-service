package org.olf.dcb.core.interaction.sierra;

import static org.mockserver.model.JsonBody.json;

import org.mockserver.client.MockServerClient;
import org.olf.dcb.test.TestResourceLoaderProvider;

import lombok.AllArgsConstructor;
import services.k_int.interaction.sierra.bibs.BibPatch;

@AllArgsConstructor
public class SierraBibsAPIFixture {
	private final MockServerClient mockServer;
	private final SierraMockServerRequests sierraMockServerRequests;
	private final SierraMockServerResponses sierraMockServerResponses;

	public SierraBibsAPIFixture(MockServerClient mockServer,
		TestResourceLoaderProvider testResourceLoaderProvider) {

		this(mockServer, new SierraMockServerRequests("/iii/sierra-api/v6/bibs"),
			new SierraMockServerResponses(
				testResourceLoaderProvider.forBasePath("classpath:mock-responses/sierra/bibs/")));
	}

	public void createGetBibsMockWithQueryStringParameters() {
		mockServer
			.when(sierraMockServerRequests.get()
				.withQueryStringParameter("updatedDate", "null")
				.withQueryStringParameter("suppressed", "false")
				.withQueryStringParameter("offset", "1")
				.withQueryStringParameter("locations", "a")
				.withQueryStringParameter("limit", "3")
				.withQueryStringParameter("deleted", "false")
				.withQueryStringParameter("createdDate", "null"))
			.respond(sierraMockServerResponses
				.jsonSuccess("sierra-api-GET-bibs-success-response.json"));
	}

	public void createPostBibsMock(BibPatch bibPatch, Integer returnId) {
		mockServer
			.when(sierraMockServerRequests.post()
				.withBody(json(bibPatch)))
			.respond(sierraMockServerResponses
				.jsonLink("https://sandbox.iii.com/iii/sierra-api/v6/bibs/" + returnId));
	}
}
