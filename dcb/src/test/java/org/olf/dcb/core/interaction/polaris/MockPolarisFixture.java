package org.olf.dcb.core.interaction.polaris;

import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.JsonBody.json;
import static org.mockserver.model.MediaType.APPLICATION_JSON;

import org.mockserver.client.MockServerClient;
import org.mockserver.model.HttpResponse;
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

	public void mockAppServicesStaffAuthentication() {
		mock("POST", "/polaris.applicationservices/api/v1/eng/20/authentication/staffuser", "auth-response.json");
	}

	public void mockPatronAuthentication() {
		mock("POST", "/PAPIService/REST/public/v1/1033/100/1/authenticator/patron", "test-patron-auth.json");
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

	public void mockGetPatron(String patronId) {
		mock("GET", "/polaris.applicationservices/api/v1/eng/20/polaris/73/1/patrons/" + patronId,
			"get-patron-by-local-id.json");
	}

	public void mockGetPatronByBarcode(String barcode) {
		mock("GET", "/PAPIService/REST/public/v1/1033/100/1/patron/" + barcode, "patron-by-barcode.json");
	}

	public void mockGetPatronBlocksSummary(String patronId) {
		mock("/polaris.applicationservices/api/v1/eng/20/polaris/73/1/patrons/%s/blockssummary"
			.formatted(patronId), "[]");
	}

	public void mockGetItemsForBib(String bibId) {
		mock("GET", "/PAPIService/REST/protected/v1/1033/100/1/string/synch/items/bibid/" + bibId, "items-get.json");
	}

	void mockGetItem(String itemId) {
		mock("GET", "/PAPIService/REST/protected/v1/1033/100/1/string/synch/item/" + itemId, "items-get.json");
	}

	void mockGetHold(String holdId) {
		mock("GET", "/polaris.applicationservices/api/v1/eng/20/polaris/73/1/holds/" + holdId, "get-hold.json");
	}

	void mockGetMaterialTypes() {
		mock("GET", "/polaris.applicationservices/api/v1/eng/20/polaris/73/1/materialtypes", "materialtypes.json");
	}

	void mockGetItemStatuses() {
		mock("GET", "/polaris.applicationservices/api/v1/eng/20/polaris/73/1/itemstatuses", "itemstatuses.json");
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
			.withBody(json(json, APPLICATION_JSON));
	}
}
