package org.olf.reshare.dcb.core.interaction.sierra;

import static org.mockserver.model.JsonBody.json;

import org.mockserver.client.MockServerClient;

import io.micronaut.core.io.ResourceLoader;
import services.k_int.interaction.sierra.bibs.BibPatch;

public class SierraBibsAPIFixture {
	private final MockServerClient mockServer;
	private final SierraMockServerRequests sierraMockServerRequests;
	private final SierraMockServerResponses sierraMockServerResponses;

	public SierraBibsAPIFixture(MockServerClient mockServer, ResourceLoader loader) {
		this.mockServer = mockServer;

		sierraMockServerRequests = new SierraMockServerRequests(
			"/iii/sierra-api/v6/bibs");

		sierraMockServerResponses = new SierraMockServerResponses(
			"classpath:mock-responses/sierra/bibs/", loader);
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

	public void createPostBibsMock(BibPatch bibPatch) {
		mockServer
			.when(sierraMockServerRequests.post()
				.withBody(json(bibPatch)))
			.respond(sierraMockServerResponses
				.jsonLink("https://sandbox.iii.com/iii/sierra-api/v6/bibs/7916922"));
	}
}
