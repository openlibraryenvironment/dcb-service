package org.olf.dcb.core.interaction.sierra;

import static org.mockserver.model.JsonBody.json;
import static org.mockserver.verify.VerificationTimes.never;
import static org.mockserver.verify.VerificationTimes.once;
import static org.olf.dcb.core.interaction.sierra.SierraMockServerResponses.badRequestError;
import static org.olf.dcb.core.interaction.sierra.SierraMockServerResponses.jsonLink;
import static org.olf.dcb.core.interaction.sierra.SierraMockServerResponses.noRecordsFound;
import static org.olf.dcb.core.interaction.sierra.SierraMockServerResponses.serverError;
import static org.olf.dcb.core.interaction.sierra.SierraMockServerResponses.thisRecordIsNotAvailable;
import static org.olf.dcb.test.MockServerCommonResponses.noContent;
import static org.olf.dcb.test.MockServerCommonResponses.okJson;
import static services.k_int.interaction.sierra.QueryEntry.buildPatronQuery;

import java.util.Collections;
import java.util.List;

import org.mockserver.matchers.Times;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.verify.VerificationTimes;
import org.olf.dcb.test.MockServer;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import services.k_int.interaction.sierra.LinkResult;
import services.k_int.interaction.sierra.QueryEntry;
import services.k_int.interaction.sierra.QueryResultSet;
import services.k_int.interaction.sierra.SierraCodeTuple;
import services.k_int.interaction.sierra.holds.SierraPatronHold;
import services.k_int.interaction.sierra.holds.SierraPatronHoldResultSet;
import services.k_int.interaction.sierra.patrons.CheckoutPatch;
import services.k_int.interaction.sierra.patrons.PatronHoldPost;
import services.k_int.interaction.sierra.patrons.PatronPatch;
import services.k_int.interaction.sierra.patrons.SierraPatronRecord;

@Slf4j
@AllArgsConstructor
public class SierraPatronsAPIFixture {
	private final SierraMockServerRequests sierraMockServerRequests;
	private final MockServer mockServer;

	public SierraPatronsAPIFixture(MockServer mockServer) {
		this(new SierraMockServerRequests(getPatronsPath()), mockServer);
	}

	public void mockGetHoldByIdNotFound(String holdId) {
		mockServer.mock(getHoldById(holdId), noRecordsFound());
	}

	public void mockGetHoldById(String holdId, SierraPatronHold hold) {
		mockGetHoldById(holdId, okJson(hold));
	}

	public void mockGetHoldById(String holdId, HttpResponse response) {
		mockServer.replaceMock(getHoldById(holdId), response);
	}

	private HttpRequest getHoldById(String holdId) {
		return sierraMockServerRequests.get("/holds/" + holdId);
	}

	public void mockDeleteHold(String holdId) {
		deleteHoldById(holdId, noContent());
	}

	public void mockDeleteHoldError(String holdId) {
		deleteHoldById(holdId, badRequestError());
	}

	public void verifyDeleteHoldRequestMade(String holdId) {
		mockServer.verify(deleteHoldRequest(holdId));
	}

	public void verifyNoDeleteHoldRequestMade(String holdId) {
		mockServer.verifyNever(deleteHoldRequest(holdId));
	}

	public void verifyNoDeleteHoldRequestMade() {
		mockServer.verifyNever(deleteHoldRequest());
	}

	private void deleteHoldById(String holdId, HttpResponse response) {
		mockServer.replaceMock(deleteHoldRequest(holdId), response);
	}

	private HttpRequest deleteHoldRequest(String holdId) {
		return sierraMockServerRequests.delete("/holds/" + holdId);
	}

	private HttpRequest deleteHoldRequest() {
		return sierraMockServerRequests.delete("/holds/*");
	}

	public void postPatronResponse(String uniqueId, int returnId) {
		mockServer.mock(postPatronRequest(uniqueId), patronPlacedResponse(returnId));
	}

	public void postPatronErrorResponse(String uniqueId) {
		mockServer.mock(postPatronRequest(uniqueId), badRequestError());
	}

	public void mockRenewalSuccess(String checkoutID) {
		mockServer.replaceMock(postRenewal(checkoutID), "items/sierra-api-renewal-success.json");
	}

	public void mockRenewalNoRecordsFound(String checkoutID) {
		mockServer.replaceMock(postRenewal(checkoutID), noRecordsFound());
	}

	public void verifyRenewalRequestMade(String checkoutId) {
		mockServer.verify(postRenewal(checkoutId));
	}

	public void verifyNoCheckoutRelatedRequestsMade() {
		// This is a compromise because mock server seems to only support wildcards at the end of the path
		mockServer.verifyNever(sierraMockServerRequests.post("/checkouts/*"));
	}

	public void thisRecordIsNotAvailableResponse(String patronId, String expectedRecordType) {
		mockServer.mock(postPatronHoldRequest(patronId, PatronHoldPost.builder()
				.recordType(expectedRecordType)
				.recordNumber(null)
				.build()), thisRecordIsNotAvailable());
	}

	public void verifyCreatePatronRequestMade(String uniqueId) {
		mockServer.verify(postPatronRequest(uniqueId));
	}

	public void verifyCreatePatronRequestNotMade(String uniqueId) {
		mockServer.verifyNever(postPatronRequest(uniqueId));
	}

	private HttpRequest postPatronRequest(String uniqueId) {
		final var patronPatch = PatronPatch.builder()
			.uniqueIds(List.of(uniqueId))
			.build();

		return sierraMockServerRequests.post(patronPatch);
	}

	private HttpRequest postRenewal(String checkoutID) {
		return sierraMockServerRequests.post("/checkouts/" + checkoutID + "/renewal");
	}

	public void verifyCheckoutMade(String itemBarcode, String patronBarcode, String pin) {
		mockServer.verify(checkOutItemToPatron(itemBarcode, patronBarcode, pin));
	}

	public void checkOutItemToPatron(String itemBarcode, String patronBarcode) {
		mockServer.mock(checkOutItemToPatron(itemBarcode, patronBarcode, null), checkedOutItemToPatron());
	}

	private HttpRequest checkOutItemToPatron(String itemBarcode, String patronBarcode, String pin) {
		final var checkoutPatch = CheckoutPatch.builder()
			.itemBarcode(itemBarcode)
			.patronBarcode(patronBarcode)
			.patronPin(pin)
			.build();

		return sierraMockServerRequests.post("/checkout", checkoutPatch);
	}

	public void getPatronByLocalIdSuccessResponse(String id, SierraPatronRecord patron) {
		mockServer.mock(getPatron(id), okJson(patron));
	}

	public void verifyGetPatronByLocalIdRequestMade(String id) {
		mockServer.verify(sierraMockServerRequests.get("/" + id)
				// Strictly expect the fields parameter because
				// a field not being listed can quietly lead to unintended
				// behaviour when later code tries to use a field that will never be provided
				.withQueryStringParameter("fields",
					"id,updatedDate,createdDate,expirationDate,names,barcodes,patronType,homeLibraryCode,emails,message,uniqueIds,emails,fixedFields,blockInfo,autoBlockInfo,deleted"));
	}

	public void noRecordsFoundWhenGettingPatronByLocalId(String patronId) {
		mockServer.mock(getPatron(patronId), noRecordsFound());
	}

	public void badRequestWhenGettingPatronByLocalId(String patronId) {
		mockServer.mock(getPatron(patronId), badRequestError());
	}

	public void updatePatron(String patronId) {
		mockServer.mock(putPatron(patronId), noContent());
	}

	public void verifyUpdatePatronRequestMade(String expectedPatronId) {
		verifyUpdatePatronRequest(expectedPatronId, once());
	}

	public void verifyUpdatePatronRequestNotMade(String expectedPatronId) {
		verifyUpdatePatronRequest(expectedPatronId, never());
	}

	private void verifyUpdatePatronRequest(String expectedPatronId, VerificationTimes times) {
		mockServer.verify(putPatron(expectedPatronId), times);
	}

	private HttpRequest putPatron(String patronId) {
		return sierraMockServerRequests.put("/" + patronId);
	}

	public void patronsQueryFoundResponse(String uniqueId, String localPatronId) {

		final var queryEntry = buildPatronQuery(uniqueId);

		mockServer.mock(patronsQuery(queryEntry), successfulPatronsQuery(localPatronId));
	}

	public void verifyPatronQueryRequestMade(String uniqueId) {
		mockServer.verify(patronsQuery(buildPatronQuery(uniqueId)));
	}

	public void patronsQueryNotFoundResponse(String uniqueId) {
		mockServer.mock(patronsQuery(buildPatronQuery(uniqueId)), unsuccessfulPatronsQuery());
	}

	public void patronFoundResponse(String tag, String uniqueId, SierraPatronRecord patron) {
		mockServer.mock(findPatron(tag, uniqueId), patron);
	}

	public void patronNotFoundResponse(String tag, String content) {
		mockServer.mock(findPatron(tag, content), noRecordsFound());
	}

	private HttpRequest findPatron(String tag, String content) {
		return sierraMockServerRequests.get("/find")
			.withQueryStringParameter("varFieldTag", tag)
			.withQueryStringParameter("varFieldContent", content);
	}

	private HttpRequest patronsQuery(QueryEntry queryEntry) {
		return sierraMockServerRequests.post("/query", queryEntry)
			.withQueryStringParameter("offset", "0")
			.withQueryStringParameter("limit", "10");
	}

	public void mockPlacePatronHoldRequest(String patronId,
		String expectedRecordType, Integer expectedRecordNumber) {

		final var request = postPatronHoldRequest(patronId, PatronHoldPost.builder()
			.recordType(expectedRecordType)
			.recordNumber(expectedRecordNumber)
			.build());

		mockServer.mock(request, noContent());
	}

	public void patronHoldRequestErrorResponse(String patronId, String expectedRecordType) {
		final var request = postPatronHoldRequest(patronId, PatronHoldPost.builder()
			.recordType(expectedRecordType)
			.recordNumber(null)
			.build());

		mockServer.mock(request, serverError());
	}

	public void verifyPlaceHoldRequestMade(String expectedPatronId,
		String expectedRecordType, int expectedRecordNumber, String expectedPickupLocation,
		String expectedNote) {

		mockServer.verify(postPatronHoldRequest(expectedPatronId,
			PatronHoldPost.builder()
				.recordType(expectedRecordType)
				.recordNumber(expectedRecordNumber)
				.pickupLocation(expectedPickupLocation)
				.note(expectedNote)
				.build()));
	}

	private HttpRequest postPatronHoldRequest(String patronId, PatronHoldPost holdRequest) {
		return sierraMockServerRequests.post("/" + patronId + "/holds/requests")
			.withBody(json(holdRequest));
	}

	public void mockGetHoldsForPatron(String patronId) {
		mockServer.mock(getPatronHolds(patronId), "patrons/sierra-api-patron-hold.json", Times.once());
	}

	public void mockGetHoldsForPatronReturningSingleItemHold(String patronId,
		String holdIdUrl, String note, String itemId) {

		final var holds = List.of(
			SierraPatronHold.builder()
				.id(holdIdUrl)
				.recordType("i")
				.record("https://some/record/" + itemId)
				.patron("https://some/patron/" + patronId)
				.frozen("false")
				.notWantedBeforeDate("2023-08-01")
				.status(SierraCodeTuple.builder()
					.code("0")
					.name("On hold")
					.build())
				.pickupLocation(SierraCodeTuple.builder()
					.code("21")
					.name("Vineland Branch")
					.build())
				.note(note)
				.build()
		);

		mockServer.replaceMock(getPatronHolds(patronId), createHoldResultSet(holds));
	}

	public void mockGetHoldsForPatronReturningSingleBibHold(String patronId,
		String holdIdUrl, String note, String bibId) {

		final var holds = List.of(
			SierraPatronHold.builder()
				.id(holdIdUrl)
				.recordType("b")
				.record("https://some/record/" + bibId)
				.patron("https://some/patron/" + patronId)
				.frozen("false")
				.notWantedBeforeDate("2023-08-01")
				.status(SierraCodeTuple.builder()
					.code("0")
					.name("On hold")
					.build())
				.pickupLocation(SierraCodeTuple.builder()
					.code("21")
					.name("Vineland Branch")
					.build())
				.note(note)
				.build()
		);

		mockServer.replaceMock(getPatronHolds(patronId), createHoldResultSet(holds));
	}

	public void patronHoldNotFoundErrorResponse(String patronId) {
		mockServer.mock(getPatronHolds(patronId), noRecordsFound());
	}

	public void patronHoldErrorResponse(String id) {
		mockServer.mock(getPatronHolds(id), badRequestError());
	}

	public void addPatronGetExpectation(String patronId) {
		mockServer.mock(getPatron(patronId), "patrons/patron/" + patronId + ".json");
	}

	private QueryResultSet successfulPatronsQuery(String localPatronId) {
		return QueryResultSet.builder()
			.total(1)
			.start(0)
			.entries(Collections.singletonList(
				LinkResult.builder().link("https://sandbox.iii.com/iii/sierra-api/v6/patrons/"+localPatronId).build()
			))
			.build();
	}

	private HttpResponse unsuccessfulPatronsQuery() {
		final var queryResultSet = QueryResultSet.builder()
			.total(0)
			.start(0)
			.entries(List.of())
			.build();

		return okJson(queryResultSet);
	}

	private HttpResponse patronPlacedResponse(int patronId) {
		return jsonLink("https://sandbox.iii.com/iii/sierra-api/v6/patrons/" + patronId);
	}

	private HttpResponse checkedOutItemToPatron() {
		return jsonLink("https://sandbox.iii.com/iii/sierra-api/v6/patrons/checkout/testPatronId");
	}

	private HttpRequest getPatronHolds(String patronId) {
		return sierraMockServerRequests.get("/" + patronId + "/holds");
	}

	private HttpRequest getPatron(String patronId) {
		return sierraMockServerRequests.get("/" + patronId);
	}

	private static String getPatronsPath() {
		return "/iii/sierra-api/v6/patrons";
	}

	private static SierraPatronHoldResultSet createHoldResultSet(List<SierraPatronHold> holds) {
		return SierraPatronHoldResultSet.builder()
			.total(holds.size())
			.start(0)
			.entries(holds)
			.build();
	}
}
