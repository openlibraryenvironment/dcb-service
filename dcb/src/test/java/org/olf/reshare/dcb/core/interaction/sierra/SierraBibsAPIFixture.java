package org.olf.reshare.dcb.core.interaction.sierra;

import io.micronaut.core.io.ResourceLoader;
import org.mockserver.client.MockServerClient;
import services.k_int.interaction.sierra.bibs.BibPatch;

import java.io.IOException;

import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.JsonBody.json;
import static org.mockserver.model.MediaType.APPLICATION_JSON;

public class SierraBibsAPIFixture {
	private static final String BIB_PATH = "/iii/sierra-api/v6/bibs";
	private final String MOCK_ROOT = "classpath:mock-responses/sierra/bibs";

	private final MockServerClient mock;
	private final ResourceLoader loader;

	public SierraBibsAPIFixture(MockServerClient mock, ResourceLoader loader) {
		this.mock = mock;
		this.loader = loader;
	}

	public void createGetBibsMockWithQueryStringParameters() throws IOException {
		mock.when(
			request()
				.withHeader("Accept", "application/json")
				.withMethod("GET")
				.withPath(BIB_PATH)
				.withQueryStringParameter("updatedDate", "null")
				.withQueryStringParameter("suppressed", "false")
				.withQueryStringParameter("offset", "1")
				.withQueryStringParameter("locations", "a")
				.withQueryStringParameter("limit", "3")
				.withQueryStringParameter("deleted", "false")
				.withQueryStringParameter("createdDate", "null")
		).respond(
			response()
				.withStatusCode(200)
				.withContentType(org.mockserver.model.MediaType.APPLICATION_JSON)
				.withBody(
					json(new String(loader
						.getResourceAsStream(MOCK_ROOT + "/sierra-api-GET-bibs-success-response.json")
						.orElseThrow()
						.readAllBytes()))));
	}

	public void createPostBibsMock(BibPatch bibPatch, Integer returnId) {
		mock.when(
			request()
				.withHeader("Accept", "application/json")
				.withMethod("POST")
				.withPath(BIB_PATH)
				.withBody(json(bibPatch))
		).respond(
			response()
				.withStatusCode(200)
				.withContentType(APPLICATION_JSON)
				.withBody(json("{\"link\": \"https://sandbox.iii.com/iii/sierra-api/v6/bibs/" + returnId + "\"}")));
	}
}
