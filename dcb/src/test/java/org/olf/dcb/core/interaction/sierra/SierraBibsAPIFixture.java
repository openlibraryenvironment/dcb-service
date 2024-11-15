package org.olf.dcb.core.interaction.sierra;

import lombok.AllArgsConstructor;
import org.mockserver.client.MockServerClient;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.olf.dcb.test.TestResourceLoaderProvider;
import services.k_int.interaction.sierra.bibs.BibPatch;

import java.util.List;

import static org.mockserver.model.JsonBody.json;

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

	public void mockDeleteBib(String bibId) {
		deleteBib(bibId, sierraMockServerResponses.noContent());
	}

	private void deleteBib(String bibId, HttpResponse response) {
		mockServer.clear(deleteBibRecord(bibId));

		mockServer.when(deleteBibRecord(bibId))
			.respond(response);
	}

	private HttpRequest deleteBibRecord(String bibId) {
		return sierraMockServerRequests.delete("/" + bibId);
	}

	public static BibPatch COMMON_BIB_PATCH() {
		return BibPatch.builder()
			.authors(List.of("Stafford Beer"))
			.titles(List.of("Brain of the Firm"))
			.build();
	}
}
