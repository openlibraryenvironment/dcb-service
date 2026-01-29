package org.olf.dcb.core.interaction.polaris;

import static io.micronaut.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.notFoundResponse;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.JsonBody.json;
import static org.mockserver.model.MediaType.APPLICATION_JSON;
import static org.mockserver.verify.VerificationTimes.once;
import static org.olf.dcb.core.interaction.polaris.ApplicationServicesClient.WorkflowResponse;

import java.util.List;

import org.mockserver.client.MockServerClient;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.JsonBody;
import org.olf.dcb.core.interaction.polaris.ApplicationServicesClient.BibliographicRecord;
import org.olf.dcb.core.interaction.polaris.ApplicationServicesClient.ItemRecordFull;
import org.olf.dcb.core.interaction.polaris.ApplicationServicesClient.LibraryHold;
import org.olf.dcb.core.interaction.polaris.ApplicationServicesClient.RequestExtension;
import org.olf.dcb.core.interaction.polaris.ApplicationServicesClient.RequestExtensionData;
import org.olf.dcb.core.interaction.polaris.ApplicationServicesClient.SysHoldRequest;
import org.olf.dcb.core.interaction.polaris.ApplicationServicesClient.WorkflowRequest;
import org.olf.dcb.core.interaction.polaris.PAPIClient.PatronRegistration;
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
		mockPost(protectedPapiServicePath("/authenticator/staff"), "test-staff-auth.json");
	}

	public void mockAppServicesStaffAuthentication() {
		mockPost("/polaris.applicationservices/api/v1/eng/20/authentication/staffuser",
			"auth-response.json");
	}

	public void mockPatronAuthentication() {
		mockPost(publicPapiServicePath("/authenticator/patron"), "test-patron-auth.json");
	}

	public void mockCreatePatron() {
		mockPost(publicPapiServicePath("/patron"), "create-patron.json");
	}

	public void mockCreatePatron(PAPIClient.PatronRegistrationCreateResult response) {
		mockPost(publicPapiServicePath("/patron"), okJson(response));
	}

	public void mockUpdatePatron(String patronBarcode) {
		mockPut(patronByBarcodePath(patronBarcode), "update-patron.json");
	}

	public void verifyUpdatePatron(String barcode, PatronRegistration expectedUpdate) {
		mockServerClient.verify(request()
			.withHeader("Accept", "application/json")
			.withHeader("host", host)
			.withMethod("PUT")
			.withPath(patronByBarcodePath(barcode))
			.withBody(json(expectedUpdate))
		);
	}

	public void mockPatronSearch(String firstMiddleLastName) {
		mockServerClient.when(patronSearchRequest(firstMiddleLastName))
			.respond(okJson(getResource("patron-search.json")));
	}

	public void mockPatronSearchPapiError(String firstMiddleLastName,
		int papiErrorCode, String errorMessage) {

		mockServerClient.when(patronSearchRequest(firstMiddleLastName))
			.respond(okJson(PAPIClient.PatronSearchResult.builder()
				.papiErrorCode(papiErrorCode)
				.ErrorMessage(errorMessage)
				.build()));
	}

	public void verifyPatronSearch(String firstMiddleLastName) {
		mockServerClient.verify(patronSearchRequest(firstMiddleLastName));
	}

	private HttpRequest patronSearchRequest(String firstMiddleLastName) {
		return request()
			.withHeader("Accept", "application/json")
			.withHeader("host", host)
			.withMethod("GET")
			.withPath(protectedPapiServicePath("/string/search/patrons/boolean*"))
			.withQueryStringParameter("q", "PATNF=" + firstMiddleLastName);
	}

	public void mockGetPatron(String patronId, ApplicationServicesClient.PatronData patron) {
		mockGet(patronByIdPath(patronId), patron);
	}

	public void mockGetPatronServerErrorResponse(String patronId) {
		mockGet(patronByIdPath(patronId), HttpResponse.response()
			.withStatusCode(500)
			.withBody("Something went wrong"));
	}

	public void mockGetPatronByBarcode(String barcode) {
		mockGet(patronByBarcodePath(barcode), "patron-by-barcode.json");
	}

	public void mockGetPatronCirculationBlocks(String barcode,
		PAPIClient.PatronCirculationBlocksResult response) {

		mockGet(patronByBarcodePath(barcode) + "/circulationblocks", okJson(response));
	}

	public void mockGetPatronBlocksSummary(String patronId) {
		String path = "/polaris.applicationservices/api/v1/eng/20/polaris/73/1/patrons/%s/blockssummary"
			.formatted(patronId);

		mockGet(path, okText("[]"));
	}

	public void mockGetPatronBlocksSummaryNotFoundResponse(String patronId) {
		String path = "/polaris.applicationservices/api/v1/eng/20/polaris/73/1/patrons/%s/blockssummary"
			.formatted(patronId);

		mockGet(path, notFoundResponse());
	}

	public void mockGetPatronBlocksSummaryServerErrorResponse(String patronId) {
		String path = "/polaris.applicationservices/api/v1/eng/20/polaris/73/1/patrons/%s/blockssummary"
			.formatted(patronId);

		mockGet(path, HttpResponse.response().withStatusCode(INTERNAL_SERVER_ERROR.getCode()));
	}

	public void mockGetPatronBarcode(String patronId, String barcode) {
		String body = "\"%s\"".formatted(barcode);

		mockGet("/polaris.applicationservices/api/v1/eng/20/polaris/73/1/barcodes/patrons/" + patronId, okText(body));
	}

	public void mockCheckoutItemToPatron(String localPatronBarcode) {
		mockPost(patronItemCheckOutPath(localPatronBarcode), "itemcheckoutsuccess.json");
	}

	public void mockRenewalSuccess(String localPatronBarcode) {
		mockPost(patronItemCheckOutPath(localPatronBarcode), "renewal-success.json");
	}

	public void mockRenewalItemBlockedError(String localPatronBarcode) {
		mockPost(patronItemCheckOutPath(localPatronBarcode), "renewal-item-blocked.json");
	}

	private static String patronItemCheckOutPath(String localPatronBarcode) {
		return publicPapiServicePath("/patron/%s/itemsout".formatted(localPatronBarcode));
	}

	public void mockGetItemsForBib(String bibId) {
		mockGet(itemsByBibIdPath(bibId), "items-get.json");
	}

	public void mockGetItemsForBibWithShelfLocations(String bibId) {
		mockGet(itemsByBibIdPath(bibId), "items-get-with-shelf-locations.json");
	}

	private static String itemsByBibIdPath(String bibId) {
		return protectedPapiServicePath("/string/synch/items/bibid/" + bibId);
	}

	public void mockGetItem(String itemId) {
		mockGet("/polaris.applicationservices/api/v1/eng/20/polaris/73/1/itemrecords/" + itemId,
			"item-by-id.json");
	}

	public void mockGetItem(Integer itemId, ItemRecordFull expectedItem) {
		mockGet("/polaris.applicationservices/api/v1/eng/20/polaris/73/1/itemrecords/" + itemId, expectedItem);
	}

	public void mockGetItemWithNullRenewalCount(String itemId) {
		mockGet("/polaris.applicationservices/api/v1/eng/20/polaris/73/1/itemrecords/" + itemId,
			"item-by-id-without-renewal-count.json");
	}

	public void mockGetItemServerErrorResponse(String itemId) {
		mockGet("/polaris.applicationservices/api/v1/eng/20/polaris/73/1/itemrecords/" + itemId,
			response()
				.withStatusCode(500)
				.withBody("Something went wrong"));
	}

	public void mockGetItemBarcode(String localItemId, String barcode) {
		String body = "\"%s\"".formatted(barcode);
		mockGet("/polaris.applicationservices/api/v1/eng/20/polaris/73/1/barcodes/items/" + localItemId, okText(body));
	}

	void mockPlaceHold() {
		mockPost("/polaris.applicationservices/api/v1/eng/20/polaris/73/1/workflow",
			"successful-place-request.json");
	}

	public void mockPlaceHoldUnsuccessful() {
		mockPost("/polaris.applicationservices/api/v1/eng/20/polaris/73/1/workflow",
			"unsuccessful-place-request.json");
	}

	void verifyPlaceHold(RequestExtensionData expectedRequest) {
		mockServerClient.verify(baselineRequest("POST",
				"/polaris.applicationservices/api/v1/eng/20/polaris/73/1/workflow")
			.withBody(json(WorkflowRequest.builder()
				.workflowRequestType(5)
				.txnBranchID(73)
				.txnUserID(1)
				.txnWorkstationID(1)
				// Cannot match on expiration date and notes because it is generated internally
				.requestExtension(RequestExtension.builder()
					.workflowRequestExtensionType(9)
					.data(expectedRequest)
					.build())
				.build())), once());
	}

	public void mockListPatronLocalHolds() {
		mockGet("/polaris.applicationservices/api/v1/eng/20/polaris/73/1/patrons/1/requests/local",
			"listPatronLocalHolds.json");
	}

	public void mockListPatronLocalHolds(Integer patronId, SysHoldRequest expectedHold) {
		mockGet("/polaris.applicationservices/api/v1/eng/20/polaris/73/1/patrons/%s/requests/local".formatted(patronId),
			List.of(expectedHold));
	}

	public void mockEmptyListPatronLocalHolds() {
		mockGet("/polaris.applicationservices/api/v1/eng/20/polaris/73/1/patrons/1/requests/local",
			"emptyListPatronLocalHolds.json");
	}

	public void mockGetHold(String holdId, LibraryHold responseBody) {
		mockGet("/polaris.applicationservices/api/v1/eng/20/polaris/73/1/holds/" + holdId, responseBody);
	}

	public void mockGetHoldNotFound(String holdId, PolarisError response) {
		mockGet("/polaris.applicationservices/api/v1/eng/20/polaris/73/1/holds/" + holdId,
			response()
				.withStatusCode(404)
				.withBody(JsonBody.json(response)));
	}

	void mockCreateBib() {
		mockPost("/polaris.applicationservices/api/v1/eng/20/polaris/73/1/bibliographicrecords*",
			"create-bib-resp.json");
	}

	public void mockCreateBibNotAuthorisedResponse() {
		mockPost("/polaris.applicationservices/api/v1/eng/20/polaris/73/1/bibliographicrecords*",
			response().withStatusCode(401));
	}

	public void mockGetBib(String bibId) {
		mockGet("/polaris.applicationservices/api/v1/eng/20/polaris/73/1/bibliographicrecords/" + bibId + "*",
			"get-bib.json");
	}

	public void mockGetBib(Integer bibId, BibliographicRecord expectedBib) {
		mockGet("/polaris.applicationservices/api/v1/eng/20/polaris/73/1/bibliographicrecords/" + bibId + "*",
			expectedBib);
	}

	public void mockGetPagedBibs() {
		mockGet(protectedPapiServicePath("/string/synch/bibs/MARCXML/paged/*"), "bibs-slice-0-9.json");
	}

	void mockStartWorkflow(String responsePath) {
		mockPost("/polaris.applicationservices/api/v1/eng/20/polaris/73/1/workflow", responsePath);
	}

	void mockStartWorkflow(WorkflowResponse response) {
		mockPost("/polaris.applicationservices/api/v1/eng/20/polaris/73/1/workflow", response);
	}

	public void mockContinueWorkflow(String workflowId, String responsePath) {
		mockPut("/polaris.applicationservices/api/v1/eng/20/polaris/73/1/workflow/" + workflowId, responsePath);
	}

	void mockGetMaterialTypes() {
		mockGet("/polaris.applicationservices/api/v1/eng/20/polaris/73/1/materialtypes", "materialtypes.json");
	}

	void mockGetItemStatuses() {
		mockGet("/polaris.applicationservices/api/v1/eng/20/polaris/73/1/itemstatuses", "itemstatuses.json");
	}

	private static String patronByIdPath(String patronId) {
		return "/polaris.applicationservices/api/v1/eng/20/polaris/73/1/patrons/" + patronId;
	}

	private static String patronByBarcodePath(String patronBarcode) {
		return publicPapiServicePath("/patron/" + patronBarcode);
	}

	private static String protectedPapiServicePath(String subPath) {
		return papiServicePath("protected", subPath);
	}

	private static String publicPapiServicePath(String subPath) {
		return papiServicePath("public", subPath);
	}

	private static String papiServicePath(String scope, String subPath) {
		return "/PAPIService/REST/%s/v1/1033/100/1%s".formatted(scope, subPath);
	}

	private void mockPost(String path, String jsonResourcePath) {
		mockPost(path, (Object)getResource(jsonResourcePath));
	}

	private void mockPost(String path, Object responseBody) {
		mockPost(path, okJson(responseBody));
	}

	private void mockPost(String path, HttpResponse response) {
		mock("POST", path, response);
	}

	private void mockGet(String path, String jsonResourcePath) {
		mockGet(path, (Object)getResource(jsonResourcePath));
	}

	private void mockGet(String path, Object responseBody) {
		mockGet(path, okJson(responseBody));
	}

	private void mockGet(String path, HttpResponse response) {
		mock("GET", path, response);
	}

	private void mockPut(String path, String jsonResourcePath) {
		mock("PUT", path, okJson(getResource(jsonResourcePath)));
	}

	private String getResource(String path) {
		return resourceLoader.getResource(path);
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
