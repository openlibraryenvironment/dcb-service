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

import org.checkerframework.checker.nullness.qual.NonNull;
import org.mockserver.client.MockServerClient;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
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
		mock("POST", protectedPapiServicePath("/authenticator/staff"), "test-staff-auth.json");
	}

	public void mockAppServicesStaffAuthentication() {
		mock("POST", "/polaris.applicationservices/api/v1/eng/20/authentication/staffuser", "auth-response.json");
	}

	public void mockPatronAuthentication() {
		mock("POST", publicPapiServicePath("/authenticator/patron"), "test-patron-auth.json");
	}

	public void mockCreatePatron() {
		mock("POST", publicPapiServicePath("/patron"), "create-patron.json");
	}

	public void mockCreatePatron(PAPIClient.PatronRegistrationCreateResult response) {
		mock("POST", publicPapiServicePath("/patron"),
			response()
				.withStatusCode(200)
				.withBody(json(response)));
	}

	public void mockUpdatePatron(String patronBarcode) {
		mock("PUT", patronByBarcodePath(patronBarcode), "update-patron.json");
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
			.respond(okJson(resourceLoader.getResource("patron-search.json")));
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
		mock("GET", patronByIdPath(patronId), okJson(patron));
	}

	public void mockGetPatronServerErrorResponse(String patronId) {
		mock("GET", patronByIdPath(patronId),
			response()
				.withStatusCode(500)
				.withBody("Something went wrong"));
	}

	public void mockGetPatronByBarcode(String barcode) {
		mock("GET", patronByBarcodePath(barcode), "patron-by-barcode.json");
	}

	public void mockGetPatronCirculationBlocks(String barcode,
		PAPIClient.PatronCirculationBlocksResult response) {

		mock("GET", patronByBarcodePath(barcode) + "/circulationblocks", okJson(response));
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
		mock("POST", patronItemCheckOutPath(localPatronBarcode),
			"itemcheckoutsuccess.json");
	}

	public void mockRenewalSuccess(String localPatronBarcode) {
		mock("POST", patronItemCheckOutPath(localPatronBarcode),
			"renewal-success.json");
	}

	public void mockRenewalItemBlockedError(String localPatronBarcode) {
		mock("POST", patronItemCheckOutPath(localPatronBarcode),
			"renewal-item-blocked.json");
	}

	private static String patronItemCheckOutPath(String localPatronBarcode) {
		return publicPapiServicePath("/patron/%s/itemsout".formatted(localPatronBarcode));
	}

	public void mockGetItemsForBib(String bibId) {
		mock("GET", itemsByBibIdPath(bibId), "items-get.json");
	}

	public void mockGetItemsForBibWithShelfLocations(String bibId) {
		mock("GET", itemsByBibIdPath(bibId), "items-get-with-shelf-locations.json");
	}

	private static String itemsByBibIdPath(String bibId) {
		return protectedPapiServicePath("/string/synch/items/bibid/" + bibId);
	}

	public void mockGetItem(String itemId) {
		mock("GET", "/polaris.applicationservices/api/v1/eng/20/polaris/73/1/itemrecords/" + itemId, "item-by-id.json");
	}

	public void mockGetItem(Integer itemId, ItemRecordFull expectedItem) {
		mock("GET", "/polaris.applicationservices/api/v1/eng/20/polaris/73/1/itemrecords/" + itemId,
			response()
				.withHeader("Content-Type", "application/json")
				.withStatusCode(200)
				.withBody(json(expectedItem)));
	}

	public void mockGetItemWithNullRenewalCount(String itemId) {
		mock("GET", "/polaris.applicationservices/api/v1/eng/20/polaris/73/1/itemrecords/" + itemId, "item-by-id-without-renewal-count.json");
	}

	public void mockGetItemServerErrorResponse(String itemId) {
		mock("GET", "/polaris.applicationservices/api/v1/eng/20/polaris/73/1/itemrecords/" + itemId,
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
		mock("POST", "/polaris.applicationservices/api/v1/eng/20/polaris/73/1/workflow",
			"successful-place-request.json");
	}

	public void mockPlaceHoldUnsuccessful() {
		mock("POST", "/polaris.applicationservices/api/v1/eng/20/polaris/73/1/workflow",
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
		mock("GET", "/polaris.applicationservices/api/v1/eng/20/polaris/73/1/patrons/1/requests/local",
			"listPatronLocalHolds.json");
	}

	public void mockListPatronLocalHolds(Integer patronId, SysHoldRequest expectedHold) {
		mock("GET", "/polaris.applicationservices/api/v1/eng/20/polaris/73/1/patrons/%s/requests/local".formatted(patronId),
			response()
				.withStatusCode(200)
				.withBody(json(List.of(expectedHold))));
	}

	public void mockEmptyListPatronLocalHolds() {
		mock("GET", "/polaris.applicationservices/api/v1/eng/20/polaris/73/1/patrons/1/requests/local",
			"emptyListPatronLocalHolds.json");
	}

	public void mockGetHold(String holdId, LibraryHold response) {
		mock("GET", "/polaris.applicationservices/api/v1/eng/20/polaris/73/1/holds/" + holdId,
			response()
				.withStatusCode(200)
				.withBody(json(response)
		));
	}

	public void mockGetHoldNotFound(String holdId, PolarisError response) {
		mock("GET", "/polaris.applicationservices/api/v1/eng/20/polaris/73/1/holds/" + holdId,
			response()
				.withStatusCode(404)
				.withBody(json(response)
				));
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

	public void mockGetBib(Integer bibId, BibliographicRecord expectedBib) {
		mock("GET", "/polaris.applicationservices/api/v1/eng/20/polaris/73/1/bibliographicrecords/" + bibId + "*",
			response()
				.withStatusCode(200)
				.withContentType(APPLICATION_JSON)
				.withBody(json(expectedBib)));
	}

	public void mockGetPagedBibs() {
		mock("GET", protectedPapiServicePath("/string/synch/bibs/MARCXML/paged/*"),
			"bibs-slice-0-9.json");
	}

	void mockStartWorkflow(String responsePath) {
		mock("POST", "/polaris.applicationservices/api/v1/eng/20/polaris/73/1/workflow",
			responsePath);
	}

	void mockStartWorkflow(WorkflowResponse response) {
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
