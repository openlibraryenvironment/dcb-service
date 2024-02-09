package org.olf.dcb.core.interaction.polaris;

import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.JsonBody.json;

import org.mockserver.client.MockServerClient;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.MediaType;
import org.olf.dcb.test.TestResourceLoader;
import org.olf.dcb.test.TestResourceLoaderProvider;

public class MockPolarisFixture {
	private final String host;
	private final MockServerClient mockServerClient;
	private final TestResourceLoader resourceLoader;

	public MockPolarisFixture(String host, MockServerClient mockServerClient,
		TestResourceLoaderProvider testResourceLoaderProvider) {

		this.host = host;
		this.mockServerClient = mockServerClient;
		this.resourceLoader = testResourceLoaderProvider.forBasePath("classpath:mock-responses/polaris/");
	}

	public void mockPapiStaffAuthentication() {
		mock("POST", "/PAPIService/REST/protected/v1/1033/100/1/authenticator/staff", "test-staff-auth.json");
	}

	public void mockPatronSearch(String localBarcode, String localId, String agencyCode) {
		mockServerClient.when(
			request()
				.withHeader("Accept", "application/json")
				.withHeader("host", host)
				.withMethod("GET")
				.withPath("/PAPIService/REST/protected/v1/1033/100/1/string/search/patrons/boolean*")
				.withQueryStringParameter("q",
					"PATNF=" + localBarcode + " AND PATNL=" + localId + "@" + agencyCode))
			.respond(okJson(resourceLoader.getResource("patron-search.json")));
	}

	public void mockGetItem(int localPatronId) {
		mock("GET", "/polaris.applicationservices/api/v1/eng/20/polaris/73/1/patrons/" + localPatronId,
			"get-patron-by-local-id.json");
	}

	public void mockPagedBibs() {
		mock("GET", "/PAPIService/REST/protected/v1/1033/100/1/string/synch/bibs/MARCXML/paged/*", "bibs-slice-0-9.json");
	}

	void mock(String method, String path, String jsonResource) {
		mockServerClient.when(
			request()
				.withHeader("Accept", "application/json")
				.withHeader("host", host)
				.withMethod(method)
				.withPath(path))
			.respond(okJson(resourceLoader.getResource(jsonResource)));
	}

	void mock(String path, String body) {
		mockServerClient.when(
			request()
				.withHeader("Accept", "application/json")
				.withHeader("host", host)
				.withMethod("GET")
				.withPath(path))
			.respond(response()
				.withStatusCode(200)
				.withBody(body));
	}

	private static HttpResponse okJson(Object json) {
		return response()
			.withStatusCode(200)
			.withBody(json(json, MediaType.APPLICATION_JSON));
	}
}
