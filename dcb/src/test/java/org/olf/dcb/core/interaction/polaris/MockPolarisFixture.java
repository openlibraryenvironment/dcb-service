package org.olf.dcb.core.interaction.polaris;

import static org.mockserver.model.HttpResponse.notFoundResponse;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.JsonBody.json;
import static org.mockserver.model.StringBody.subString;
import static org.olf.dcb.test.MockServerCommonResponses.okText;
import static org.olf.dcb.test.MockServerCommonResponses.serverError;
import static services.k_int.interaction.sierra.SierraTestUtils.okJson;
import static services.k_int.utils.StringUtils.convertIntegerToString;

import java.util.List;

import org.mockserver.client.MockServerClient;
import org.mockserver.matchers.Times;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.verify.VerificationTimes;
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

	public void verifyAppServicesStaffAuthentication(VerificationTimes times) {
		mockServer.verify(commonRequests.post(
			paths.baseApplicationServices("/authentication/staffuser")), times);
	}

	/**
	 * The endpoint behind PolarisLmsClient.ping() - a single Application Services GET, which makes
	 * it the least entangled way to prove how often the auth handshake actually happens.
	 */
	public void mockGetHoldRequestDefaults(Integer expirationDatePeriod) {
		mockServer.mockGet(paths.applicationServices("/holdsdefaults"),
			ApplicationServicesClient.HoldRequestDefault.builder()
				.expirationDatePeriod(expirationDatePeriod)
				.build());
	}

	/**
	 * Answers the next hold defaults request with a 401, as Polaris does when it no longer accepts
	 * a token we are still holding. Register before mockGetHoldRequestDefaults so it matches first.
	 */
	public void mockGetHoldRequestDefaultsUnauthorisedOnce() {
		mockServer.mock(commonRequests.get(paths.applicationServices("/holdsdefaults")),
			response().withStatusCode(401), Times.once());
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

	public void verifyCreatePatronBodyContains(String fragment) {
		mockServer.verify(commonRequests.post(paths.createPatron()).withBody(subString(fragment)));
	}

	public void mockUpdatePatron(String patronBarcode) {
		mockServer.mockPut(paths.patronByBarcode(patronBarcode),
			okJson(PatronUpdateResult.builder().papiErrorCode(0).build()));
	}

	public void verifyUpdatePatron(String barcode, PatronRegistration expectedUpdate) {
		mockServer.verifyPut(paths.patronByBarcode(barcode), expectedUpdate);
	}

	/**
	 * Mocks the PAPI patron date update (PUT /patron/{barcode}). Each call queues a single response,
	 * so consecutive calls describe consecutive attempts. Success is signalled by a PAPI error code
	 * of 0; a non-zero code means Polaris refused the change.
	 */
	public void mockUpdatePatronDates(String barcode, int papiErrorCode) {
		mockServer.mock(commonRequests.put(paths.patronByBarcode(barcode)),
			okJson(PatronUpdateResult.builder().papiErrorCode(papiErrorCode).build()), Times.once());
	}

	public void mockUpdatePatronDatesAlwaysFails(String barcode) {
		mockServer.replaceMock(commonRequests.put(paths.patronByBarcode(barcode)),
			okJson(PatronUpdateResult.builder().papiErrorCode(-1).build()));
	}

	public void verifyUpdatePatronDatesContains(String barcode, String expectedBodyFragment) {
		mockServer.verify(commonRequests.put(paths.patronByBarcode(barcode))
			.withBody(subString(expectedBodyFragment)));
	}

	public void verifyNoUpdatePatronDates(String barcode) {
		mockServer.verifyNever(commonRequests.put(paths.patronByBarcode(barcode)));
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

	/**
	 * Serves a raw JSON body for GET patron, so date fields can be expressed as the ISO strings
	 * Polaris actually returns (object serialization would emit them as Jackson arrays the client
	 * cannot parse).
	 */
	public void mockGetPatronRawJson(Integer patronId, String rawJson) {
		mockServer.mockGet(paths.patronById(convertIntegerToString(patronId)),
			response().withStatusCode(200).withBody(json(rawJson)));
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

	public void mockGetPatronBlocksSummary(Integer patronId,
		List<ApplicationServicesClient.PatronBlockGetRow> blocks) {

		mockServer.replaceMock(
			commonRequests.get(paths.blocksSummary(convertIntegerToString(patronId))), okJson(blocks));
	}

	public void mockDeletePatronBlock(Integer patronId, Integer blockType, Integer blockId) {
		mockServer.mock(commonRequests.delete(paths.applicationServices(
			"/patrons/%d/blocks/%d/%d".formatted(patronId, blockType, blockId))), okJson(true));
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

	/**
	 * Queues a single GET item response, so consecutive calls describe consecutive fetches - a
	 * renewal limit update reads the item before the change and again to confirm it.
	 */
	public void mockGetItemOnce(Integer itemId, ItemRecordFull expectedItem) {
		mockServer.mock(commonRequests.get(paths.getItem(itemId)), expectedItem, Times.once());
	}

	public void mockPlaceItemBlockingNote(Integer organisationId, Integer itemId) {
		mockServer.replaceMock(commonRequests.post(paths.itemBlockingNote(organisationId, itemId)),
			okJson(ApplicationServicesClient.BlockingNoteResponse.builder()
				.itemRecordID(itemId)
				.success(true)
				.build()));
	}

	public void verifyItemBlockingNoteContains(Integer organisationId, Integer itemId, String fragment) {
		mockServer.verify(commonRequests.post(paths.itemBlockingNote(organisationId, itemId))
			.withBody(subString(fragment)));
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

	public void verifyCreateBibBodyContains(String fragment) {
		mockServer.verify(createBibRequest().withBody(subString(fragment)));
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

	/*
	 Harvest v2 endpoints. PolarisLmsClient.getChunk drives two distinct flows:
	   - full harvest  : Synch_BibsPagedGet     (/synch/bibs/MARCXML/paged), paged by lastid
	   - delta harvest : Synch_BibsUpdatedPagedGet (/synch/bibs/updated/paged) for ids, then
	                     Synch_BibsByIDGet      (/synch/bibs/MARCXML) in batches of 50 for the rows
	 Each is matched on its continuation cursor so a test can queue distinct pages for one endpoint.
	*/

	public void mockGetPagedBibs(Integer lastId, Object responseBody) {
		mockServer.mock(pagedBibsRequest()
			.withQueryStringParameter("lastid", String.valueOf(lastId)), responseBody);
	}

	public void mockGetUpdatedBibs(Integer lastId, Object responseBody) {
		mockServer.mock(updatedBibsRequest()
			.withQueryStringParameter("lastid", String.valueOf(lastId)), responseBody);
	}

	public void mockGetBibsById(String bibIds, Object responseBody) {
		mockServer.mock(bibsByIdRequest()
			.withQueryStringParameter("bibids", bibIds), responseBody);
	}

	public void mockGetMaxBibId(Object responseBody) {
		mockServer.mockGet(paths.protectedPapiService("/string/synch/bibs/maxid"), responseBody);
	}

	public void mockGetPagedBibsServerError(Integer lastId) {
		mockServer.mock(pagedBibsRequest()
			.withQueryStringParameter("lastid", String.valueOf(lastId)), serverError());
	}

	public void verifyGetBibsById(String bibIds) {
		mockServer.verify(bibsByIdRequest().withQueryStringParameter("bibids", bibIds));
	}

	public void verifyGetBibsByIdCalledTimes(VerificationTimes times) {
		mockServer.verify(bibsByIdRequest(), times);
	}

	public void verifyGetUpdatedBibsCalledTimes(VerificationTimes times) {
		mockServer.verify(updatedBibsRequest(), times);
	}

	private HttpRequest pagedBibsRequest() {
		return commonRequests.get(paths.protectedPapiService("/string/synch/bibs/MARCXML/paged"));
	}

	private HttpRequest updatedBibsRequest() {
		return commonRequests.get(paths.protectedPapiService("/string/synch/bibs/updated/paged"));
	}

	private HttpRequest bibsByIdRequest() {
		return commonRequests.get(paths.protectedPapiService("/string/synch/bibs/MARCXML"));
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

	// Appends a single workflow POST response without clearing existing ones, so a flow that posts
	// to the workflow endpoint more than once (e.g. update item status, then checkout) can queue
	// each response in order.
	public void mockWorkflowResponseOnce(WorkflowResponse response) {
		mockServer.mock(commonRequests.post(paths.workflow()), response, Times.once());
	}

	public void verifyWorkflowBodyContains(String fragment) {
		mockServer.verify(commonRequests.post(paths.workflow()).withBody(subString(fragment)));
	}

	public void verifyWorkflowBodyNeverContains(String fragment) {
		mockServer.verifyNever(commonRequests.post(paths.workflow()).withBody(subString(fragment)));
	}

	public void verifyNoItemCheckout(String patronBarcode) {
		mockServer.verifyNever(commonRequests.post(paths.patronItemCheckOut(patronBarcode)));
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
