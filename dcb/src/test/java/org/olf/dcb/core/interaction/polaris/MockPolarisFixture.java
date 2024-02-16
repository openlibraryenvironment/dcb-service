package org.olf.dcb.core.interaction.polaris;

import static io.micronaut.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.notFoundResponse;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.JsonBody.json;
import static org.mockserver.model.MediaType.APPLICATION_JSON;
import static org.olf.dcb.core.interaction.polaris.ApplicationServicesClient.*;

import org.mockserver.client.MockServerClient;
import org.mockserver.model.HttpRequest;
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

	public void mockCreatePatron() {
		mock("POST", "/PAPIService/REST/public/v1/1033/100/1/patron",
			"create-patron.json");
	}

	public void mockCreatePatron(PAPIClient.PatronRegistrationCreateResult response) {
		mock("POST", "/PAPIService/REST/public/v1/1033/100/1/patron",
			response()
				.withStatusCode(200)
				.withBody(json(response)));
	}

	public void mockUpdatePatron(String patronBarcode) {
		mock("PUT", "/PAPIService/REST/public/v1/1033/100/1/patron/" + patronBarcode, "update-patron.json");
	}

	public void mockPatronSearch(String firstMiddleLastName) {
		mockServerClient.when(patronSearchRequest(firstMiddleLastName))
			.respond(okJson(resourceLoader.getResource("patron-search.json")));
	}

	public void verifyPatronSearch(String firstMiddleLastName) {
		mockServerClient.verify(patronSearchRequest(firstMiddleLastName));
	}

	private HttpRequest patronSearchRequest(String firstMiddleLastName) {
		return request()
			.withHeader("Accept", "application/json")
			.withHeader("host", host)
			.withMethod("GET")
			.withPath("/PAPIService/REST/protected/v1/1033/100/1/string/search/patrons/boolean*")
			.withQueryStringParameter("q",
				"PATNF=" + firstMiddleLastName);
	}

	public void mockGetPatron(String patronId) {
		mock("GET", "/polaris.applicationservices/api/v1/eng/20/polaris/73/1/patrons/" + patronId,
			"get-patron-by-local-id.json");
	}

	public void mockGetPatronServerErrorResponse(String patronId) {
		mock("GET", "/polaris.applicationservices/api/v1/eng/20/polaris/73/1/patrons/" + patronId,
			response()
				.withStatusCode(500)
				.withBody("Something went wrong"));
	}

	public void mockGetPatronByBarcode(String barcode) {
		mock("GET", "/PAPIService/REST/public/v1/1033/100/1/patron/" + barcode, "patron-by-barcode.json");
	}

	public void mockGetPatronBlocksSummary(String patronId) {
		String path = "/polaris.applicationservices/api/v1/eng/20/polaris/73/1/patrons/%s/blockssummary"
			.formatted(patronId);
		mock("GET", path, okText("[]"));
	}

	public void mockGetPatronBlocksSummaryNotFoundResponse(String patronId) {
		String path = "/polaris.applicationservices/api/v1/eng/20/polaris/73/1/patrons/%s/blockssummary"
			.formatted(patronId);

		mock("GET", path, notFoundResponse());
	}

	public void mockGetPatronBlocksSummaryServerErrorResponse(String patronId) {
		String path = "/polaris.applicationservices/api/v1/eng/20/polaris/73/1/patrons/%s/blockssummary"
			.formatted(patronId);

		mock("GET", path, response().withStatusCode(INTERNAL_SERVER_ERROR.getCode()));
	}

	public void mockGetPatronBarcode(String patronId, String barcode) {
		String body = "\"%s\"".formatted(barcode);
		mock("GET",
			"/polaris.applicationservices/api/v1/eng/20/polaris/73/1/barcodes/patrons/" + patronId, okText(body));
	}

	public void mockCheckoutItemToPatron(String localPatronBarcode) {
		mock("POST",
			"/PAPIService/REST/public/v1/1033/100/1/patron/%s/itemsout".formatted(localPatronBarcode),
			"itemcheckoutsuccess.json");
	}

	public void mockGetItemsForBib(String bibId) {
		mock("GET", "/PAPIService/REST/protected/v1/1033/100/1/string/synch/items/bibid/" + bibId, "items-get.json");
	}

	public void mockGetItem(String itemId) {
		mock("GET", "/PAPIService/REST/protected/v1/1033/100/1/string/synch/item/" + itemId, "items-get.json");
	}

	public void mockGetItemServerErrorResponse(String itemId) {
		mock("GET", "/PAPIService/REST/protected/v1/1033/100/1/string/synch/item/" + itemId,
			response()
				.withStatusCode(500)
				.withBody("Something went wrong"));
	}

	public void mockGetItemBarcode(String localItemId, String barcode) {
		String body = "\"%s\"".formatted(barcode);
		mock("GET",
			"/polaris.applicationservices/api/v1/eng/20/polaris/73/1/barcodes/items/" + localItemId, okText(body));
	}

	void mockPlaceHold() {
		mock("POST", "/polaris.applicationservices/api/v1/eng/20/polaris/73/1/holds*",
			"successful-place-request.json");
	}

	public void mockPlaceHold(HoldRequestResponse response) {
		mock("POST", "/polaris.applicationservices/api/v1/eng/20/polaris/73/1/holds*",
			response()
				.withStatusCode(200)
				.withBody(json(response)));
	}

	public void mockGetHold(String holdId) {
		mock("GET", "/polaris.applicationservices/api/v1/eng/20/polaris/73/1/holds/" + holdId,
			"get-hold.json");
	}

	void mockCreateBib() {
		mock("POST", "/polaris.applicationservices/api/v1/eng/20/polaris/73/1/bibliographicrecords*",
			"create-bib-resp.json");
	}

	public void mockCreateBibNotAuthorisedResponse() {
		mock("POST", "/polaris.applicationservices/api/v1/eng/20/polaris/73/1/bibliographicrecords*",
			response().withStatusCode(401));
	}

	public void mockGetBib(String bibId) {
		mock("GET", "/polaris.applicationservices/api/v1/eng/20/polaris/73/1/bibliographicrecords/" + bibId + "*",
			"get-bib.json");
	}

	public void mockGetPagedBibs() {
		mock("GET", "/PAPIService/REST/protected/v1/1033/100/1/string/synch/bibs/MARCXML/paged/*",
			"bibs-slice-0-9.json");
	}

	void mockStartWorkflow(String responsePath) {
		mock("POST", "/polaris.applicationservices/api/v1/eng/20/polaris/73/1/workflow",
			responsePath);
	}

	void mockStartWorkflow(ItemCreateResponse response) {
		mock("POST", "/polaris.applicationservices/api/v1/eng/20/polaris/73/1/workflow",
			response()
				.withStatusCode(200)
				.withBody(json(response)));

	}

	public void mockContinueWorkflow(String workflowId, String responsePath) {
		mock("PUT", "/polaris.applicationservices/api/v1/eng/20/polaris/73/1/workflow/" + workflowId,
			responsePath);
	}

	void mockGetMaterialTypes() {
		mock("GET", "/polaris.applicationservices/api/v1/eng/20/polaris/73/1/materialtypes",
			"materialtypes.json");
	}

	void mockGetItemStatuses() {
		mock("GET", "/polaris.applicationservices/api/v1/eng/20/polaris/73/1/itemstatuses",
			"itemstatuses.json");
	}

	private void mock(String method, String path, String jsonResourcePath) {
		mock(method, path, okJson(resourceLoader.getResource(jsonResourcePath)));
	}

	private void mock(String method, String path, HttpResponse response) {
		mockServerClient
			.when(baselineRequest(method, path))
			.respond(response);
	}

	private HttpRequest baselineRequest(String method, String path) {
		return request()
			.withHeader("Accept", "application/json")
			.withHeader("host", host)
			.withMethod(method)
			.withPath(path);
	}

	private static HttpResponse okJson(Object json) {
		return response()
			.withStatusCode(200)
			.withBody(json(json, APPLICATION_JSON));
	}

	private static HttpResponse okText(String body) {
		return response()
			.withStatusCode(200)
			.withBody(body);
	}
}
