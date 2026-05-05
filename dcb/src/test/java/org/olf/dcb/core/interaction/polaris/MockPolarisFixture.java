package org.olf.dcb.core.interaction.polaris;

import static org.mockserver.model.HttpResponse.notFoundResponse;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.JsonBody.json;
import static org.olf.dcb.test.MockServerCommonResponses.okText;
import static org.olf.dcb.test.MockServerCommonResponses.serverError;
import static services.k_int.interaction.sierra.SierraTestUtils.okJson;
import static services.k_int.utils.StringUtils.convertIntegerToString;

import java.util.List;

import org.mockserver.client.MockServerClient;
import org.mockserver.matchers.Times;
import org.mockserver.model.HttpRequest;
import org.olf.dcb.core.interaction.polaris.ApplicationServicesClient.BibliographicRecord;
import org.olf.dcb.core.interaction.polaris.ApplicationServicesClient.ItemRecordFull;
import org.olf.dcb.core.interaction.polaris.ApplicationServicesClient.LibraryHold;
import org.olf.dcb.core.interaction.polaris.ApplicationServicesClient.SysHoldRequest;
import org.olf.dcb.core.interaction.polaris.ApplicationServicesClient.WorkflowRequest;
import org.olf.dcb.core.interaction.polaris.ApplicationServicesClient.WorkflowResponse;
import org.olf.dcb.core.interaction.polaris.PAPIAuthFilter.PatronAuthToken;
import org.olf.dcb.core.interaction.polaris.PAPIClient.ItemGetResponse;
import org.olf.dcb.core.interaction.polaris.PAPIClient.ItemGetRow;
import org.olf.dcb.core.interaction.polaris.PAPIClient.ItemOperationResult;
import org.olf.dcb.core.interaction.polaris.PAPIClient.PatronRegistration;
import org.olf.dcb.core.interaction.polaris.PAPIClient.PatronRegistrationCreateResult;
import org.olf.dcb.core.interaction.polaris.PAPIClient.PatronSearchResult;
import org.olf.dcb.core.interaction.polaris.PAPIClient.PatronUpdateResult;
import org.olf.dcb.core.interaction.polaris.PAPIClient.PatronValidateResult;
import org.olf.dcb.test.MockServer;
import org.olf.dcb.test.MockServerCommonRequests;
import org.olf.dcb.test.TestResourceLoaderProvider;

public class MockPolarisFixture {
	private final MockServerCommonRequests commonRequests;
	private final MockServer mockServer;

	private final Paths paths = new Paths();

	public MockPolarisFixture(String host, MockServerClient mockServerClient,
		TestResourceLoaderProvider testResourceLoaderProvider) {

		this.commonRequests = new MockServerCommonRequests(host);

		this.mockServer = new MockServer(mockServerClient, commonRequests,
			testResourceLoaderProvider.forBasePath("classpath:mock-responses/polaris/"));
	}

	public void mockPapiStaffAuthentication() {
		mockServer.mockPost(paths.protectedPapiService("/authenticator/staff"),
			// Values taken from previously hard coded responses
			PAPIAuthFilter.AuthToken.builder()
				.papiErrorCode(0)
				.accessToken("string")
				.accessSecret("string")
				.errorMessage("string")
				.polarisUserID(0)
				.branchID(0)
				.authExpDate("2023-09-18T16:40:04.652Z")
				.build());
	}

	public void mockAppServicesStaffAuthentication() {
		mockServer.mockPost(paths.baseApplicationServices("/authentication/staffuser"),
			// Values taken from previously hard coded responses
			ApplicationServicesAuthFilter.AuthToken.builder()
				.accessToken("fzB8NAopx8CEwSQI5HqpMCTQrjWm1e1x")
				.accessSecret("C5UnM8pmim1hfZRQ")
				.build());
	}

	public void mockPatronAuthentication(PatronAuthToken responseBody) {
		mockServer.mockPost(paths.publicPapiService("/authenticator/patron"), responseBody);
	}

	public void mockCreatePatron(PatronRegistrationCreateResult responseBody) {
		mockServer.replaceMock(commonRequests.post(paths.createPatron()), responseBody);
	}

	public void mockUpdatePatron(String patronBarcode) {
		mockServer.mockPut(paths.patronByBarcode(patronBarcode),
			okJson(PatronUpdateResult.builder().papiErrorCode(0).build()));
	}

	public void verifyUpdatePatron(String barcode, PatronRegistration expectedUpdate) {
		mockServer.verifyPut(paths.patronByBarcode(barcode), expectedUpdate);
	}

	public void mockPatronSearch(String firstLastName, String barcode, Integer patronId) {
		mockServer.mock(patronSearchRequest(firstLastName),
			okJson(PatronSearchResult.builder()
				.papiErrorCode(1)
				.PatronSearchRows(List.of(
					PAPIClient.PatronSearchRow.builder()
						.PatronID(patronId)
						.Barcode(barcode)
						.PatronFirstLastName(firstLastName)
						.OrganizationID(18)
						.build()
				))
				.TotalRecordsFound(1)
				.WordList("DCB testid@testagency ")
				.build()));
	}

	public void mockPatronSearchPapiError(String firstLastName, int papiErrorCode, String errorMessage) {
		mockServer.mock(patronSearchRequest(firstLastName),
			okJson(PatronSearchResult.builder()
				.papiErrorCode(papiErrorCode)
				.ErrorMessage(errorMessage)
				.build()));
	}

	public void verifyPatronSearch(String firstMiddleLastName) {
		mockServer.verify(patronSearchRequest(firstMiddleLastName));
	}

	private HttpRequest patronSearchRequest(String firstLastName) {
		return commonRequests.get(paths.protectedPapiService("/string/search/patrons/boolean*"),
			"q", "PATNF=" + firstLastName);
	}

	public void mockGetPatron(Integer patronId, ApplicationServicesClient.PatronData patron) {
		mockGetPatron(convertIntegerToString(patronId), patron);
	}

	public void mockGetPatron(String patronId, ApplicationServicesClient.PatronData patron) {
		mockServer.mockGet(paths.patronById(patronId), patron);
	}

	public void mockGetPatronServerErrorResponse(String patronId) {
		mockServer.mockGet(paths.patronById(patronId), serverError());
	}

	public void mockGetPatronByBarcode(String barcode, PatronValidateResult responseBody) {
		mockServer.mockGet(paths.patronByBarcode(barcode), responseBody);
	}

	public void mockGetPatronCirculationBlocks(String barcode,
		PAPIClient.PatronCirculationBlocksResult response) {

		mockServer.mockGet(paths.patronByBarcode(barcode) + "/circulationblocks", okJson(response));
	}

	public void mockGetPatronBlocksSummary(Integer patronId) {
		mockGetPatronBlocksSummary(convertIntegerToString(patronId));
	}

	public void mockGetPatronBlocksSummary(String patronId) {
		mockServer.replaceMock(commonRequests.get(paths.blocksSummary(patronId)), okText("[]"));
	}

	public void mockGetPatronBlocksSummaryNotFoundResponse(Integer patronId) {
		mockGetPatronBlocksSummaryNotFoundResponse(convertIntegerToString(patronId));
	}

	public void mockGetPatronBlocksSummaryNotFoundResponse(String patronId) {
		mockServer.replaceMock(commonRequests.get(paths.blocksSummary(patronId)), notFoundResponse());
	}

	public void mockGetPatronBlocksSummaryServerErrorResponse(Integer patronId) {
		mockGetPatronBlocksSummaryServerErrorResponse(convertIntegerToString(patronId));
	}

	public void mockGetPatronBlocksSummaryServerErrorResponse(String patronId) {
		mockServer.replaceMock(commonRequests.get(paths.blocksSummary(patronId)), serverError());
	}

	public void mockGetPatronBarcode(Integer patronId, String barcode) {
		mockServer.mockGet(paths.applicationServices("/barcodes/patrons/" + patronId),
			okText("\"%s\"".formatted(barcode)));
	}

	public void mockItemCheckout(String localPatronBarcode, ItemOperationResult response) {
		mockServer.mockPost(paths.patronItemCheckOut(localPatronBarcode), response);
	}

	public void mockGetItemsForBib(Integer bibId, List<ItemGetRow> expectedItems) {
		mockServer.mockGet(paths.itemsByBibId(bibId), okJson(
			ItemGetResponse.builder()
				.ItemGetRows(expectedItems)
				.build()));
	}

	public void mockGetItem(Integer itemId, ItemRecordFull expectedItem) {
		mockServer.mockGet(paths.getItem(itemId), expectedItem);
	}

	public void mockGetItemServerErrorResponse(Integer itemId) {
		mockServer.mockGet(paths.getItem(itemId), serverError());
	}

	public void mockGetItemBarcode(Integer localItemId, String barcode) {
		mockServer.mockGet(paths.getItemByBarcode(localItemId),
			okText("\"%s\"".formatted(barcode)));
	}

	public void mockListPatronLocalHolds(Integer patronId, SysHoldRequest hold) {
		mockListPatronLocalHolds(patronId, List.of(hold));
	}

	public void mockListPatronLocalHolds(Integer patronId, List<SysHoldRequest> holds) {
		mockServer.mockGet(paths.localRequests(patronId), holds);
	}

	public void mockGetHold(Integer holdId, LibraryHold responseBody) {
		mockGetHold(convertIntegerToString(holdId), responseBody);
	}

	public void mockGetHold(String holdId, LibraryHold responseBody) {
		mockServer.mockGet(paths.getHold(holdId), responseBody);
	}

	public void mockGetHoldNotFound(Integer holdId, PolarisError response) {
		mockGetHoldNotFound(convertIntegerToString(holdId), response);
	}

	public void mockGetHoldNotFound(String holdId, PolarisError response) {
		mockServer.mockGet(paths.getHold(holdId),
			response()
				.withStatusCode(404)
				.withBody(json(response)));
	}

	public void mockCreateBib(Integer bibId) {
		mockServer.replaceMock(createBibRequest(), bibId);
	}

	public void mockCreateBibNotAuthorisedResponse() {
		mockServer.replaceMock(createBibRequest(), response().withStatusCode(401));
	}

	private HttpRequest createBibRequest() {
		return commonRequests.post(paths.applicationServices("/bibliographicrecords*"));
	}

	public void mockGetBib(Integer bibId, BibliographicRecord expectedBib) {
		mockServer.mockGet(paths.getBib(bibId), expectedBib);
	}

	public void mockGetPagedBibs() {
		mockServer.mockGet(paths.protectedPapiService("/string/synch/bibs/MARCXML/paged/*"),
			"bibs-slice-0-9.json");
	}

	void mockStartWorkflow(WorkflowResponse response) {
		mockServer.replaceMock(commonRequests.post(paths.workflow()), okJson(response), Times.once());
	}

	public void mockContinueWorkflow(String workflowRequestId, WorkflowResponse response) {
		mockServer.mock(commonRequests.put(paths.workflow() + "/" + workflowRequestId),
			okJson(response), Times.once());
	}

	public void verifyWorkflow(WorkflowRequest expectedBody) {
		mockServer.verifyPost(paths.workflow(), expectedBody);
	}

	void mockGetMaterialTypes(List<ApplicationServicesClient.MaterialType> responseBody) {
		mockServer.replaceMock(commonRequests.get(paths.applicationServices("/materialtypes")),
			responseBody);
	}

	void mockGetItemStatuses(List<PolarisLmsClient.PolarisItemStatus> responseBody) {
		mockServer.replaceMock(commonRequests.get(paths.applicationServices("/itemstatuses")),
			responseBody);
	}
}
