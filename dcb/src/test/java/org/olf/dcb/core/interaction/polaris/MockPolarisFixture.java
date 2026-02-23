package org.olf.dcb.core.interaction.polaris;

import static java.util.UUID.randomUUID;
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
import org.olf.dcb.core.interaction.polaris.ApplicationServicesClient.InformationMessage;
import org.olf.dcb.core.interaction.polaris.ApplicationServicesClient.ItemRecordFull;
import org.olf.dcb.core.interaction.polaris.ApplicationServicesClient.LibraryHold;
import org.olf.dcb.core.interaction.polaris.ApplicationServicesClient.RequestExtension;
import org.olf.dcb.core.interaction.polaris.ApplicationServicesClient.RequestExtensionData;
import org.olf.dcb.core.interaction.polaris.ApplicationServicesClient.SysHoldRequest;
import org.olf.dcb.core.interaction.polaris.ApplicationServicesClient.WorkflowRequest;
import org.olf.dcb.core.interaction.polaris.ApplicationServicesClient.WorkflowResponse;
import org.olf.dcb.core.interaction.polaris.PAPIClient.PatronRegistration;
import org.olf.dcb.core.interaction.polaris.PAPIClient.PatronRegistrationCreateResult;
import org.olf.dcb.core.interaction.polaris.PAPIClient.PatronSearchResult;
import org.olf.dcb.core.interaction.polaris.PAPIClient.PatronUpdateResult;
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
		mockServer.mockPost(paths.protectedPapiService("/authenticator/staff"), "test-staff-auth.json");
	}

	public void mockAppServicesStaffAuthentication() {
		mockServer.mockPost(paths.baseApplicationServices("/authentication/staffuser"), "auth-response.json");
	}

	public void mockPatronAuthentication() {
		mockServer.mockPost(paths.publicPapiService("/authenticator/patron"), "test-patron-auth.json");
	}

	public void mockCreatePatron(PatronRegistrationCreateResult response) {
		mockServer.replaceMock(commonRequests.post(paths.createPatron()), response);
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

	public void mockGetPatronByBarcode(String barcode) {
		mockServer.mockGet(paths.patronByBarcode(barcode), "patron-by-barcode.json");
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

	public void mockCheckoutItemToPatron(String localPatronBarcode) {
		mockServer.mockPost(paths.patronItemCheckOut(localPatronBarcode), "itemcheckoutsuccess.json");
	}

	public void mockRenewalSuccess(String localPatronBarcode) {
		mockServer.mockPost(paths.patronItemCheckOut(localPatronBarcode), "renewal-success.json");
	}

	public void mockRenewalItemBlockedError(String localPatronBarcode) {
		mockServer.mockPost(paths.patronItemCheckOut(localPatronBarcode), "renewal-item-blocked.json");
	}

	public void mockGetItemsForBib(Integer bibId) {
		mockServer.mockGet(paths.itemsByBibId(bibId), "items-get.json");
	}

	public void mockGetItemsForBibWithShelfLocations(Integer bibId) {
		mockServer.mockGet(paths.itemsByBibId(bibId), "items-get-with-shelf-locations.json");
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

	void mockPlaceHold() {
		mockServer.replaceMock(commonRequests.post(paths.workflow()),
			WorkflowResponse.builder()
				.workflowRequestGuid(randomUUID().toString())
				.workflowStatus(1)
				.informationMessages(List.of(
					InformationMessage.builder()
						.type(1)
						.title("")
						.message("The hold request has been created.")
						.build()))
				.build());
	}

	public void mockPlaceHoldUnsuccessful() {
		mockServer.replaceMock(commonRequests.post(paths.workflow()), "unsuccessful-place-request.json");
	}

	void verifyPlaceHold(RequestExtensionData expectedRequest) {
		mockServer.verifyPost(paths.workflow(), WorkflowRequest.builder()
			.workflowRequestType(5)
			.txnBranchID(73)
			.txnUserID(1)
			.txnWorkstationID(1)
			// Cannot match on expiration date and notes because it is generated internally
			.requestExtension(RequestExtension.builder()
				.workflowRequestExtensionType(9)
				.data(expectedRequest)
				.build())
			.build());
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

	void mockGetMaterialTypes() {
		mockServer.mockGet(paths.applicationServices("/materialtypes"), "materialtypes.json");
	}

	void mockGetItemStatuses() {
		mockServer.mockGet(paths.applicationServices("/itemstatuses"), "itemstatuses.json");
	}
}
