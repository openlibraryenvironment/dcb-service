package org.olf.dcb.core.interaction.sierra;

import static java.util.Arrays.asList;
import static org.mockserver.model.JsonBody.json;
import static org.mockserver.verify.VerificationTimes.never;
import static org.mockserver.verify.VerificationTimes.once;
import static services.k_int.interaction.sierra.QueryEntry.buildPatronQuery;

import java.util.Collections;
import java.util.List;

import org.mockserver.client.MockServerClient;
import org.mockserver.matchers.Times;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.RequestDefinition;
import org.mockserver.verify.VerificationTimes;
import org.olf.dcb.test.TestResourceLoaderProvider;

import io.micronaut.serde.annotation.Serdeable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import services.k_int.interaction.sierra.QueryEntry;
import services.k_int.interaction.sierra.QueryResultSet;
import services.k_int.interaction.sierra.LinkResult;
import services.k_int.interaction.sierra.holds.SierraPatronHold;
import services.k_int.interaction.sierra.patrons.CheckoutPatch;

@Slf4j
@AllArgsConstructor
public class SierraPatronsAPIFixture {
	private final MockServerClient mockServer;
	private final SierraMockServerRequests sierraMockServerRequests;
	private final SierraMockServerResponses sierraMockServerResponses;

	public SierraPatronsAPIFixture(MockServerClient mockServer,
	 TestResourceLoaderProvider testResourceLoaderProvider) {

		this(mockServer, new SierraMockServerRequests("/iii/sierra-api/v6/patrons"),
			new SierraMockServerResponses(
				testResourceLoaderProvider.forBasePath("classpath:mock-responses/sierra/")));
	}

	public void mockGetHoldByIdNotFound(String holdId) {
		mockServer.when(getHoldById(holdId))
			.respond(sierraMockServerResponses.noRecordsFound());
	}

	public void mockGetHoldById(String holdId, SierraPatronHold hold) {
		mockGetHoldById(holdId, sierraMockServerResponses.jsonSuccess(json(hold)));
	}

	public void mockGetHoldById(String holdId, HttpResponse response) {
		mockServer.clear(getHoldById(holdId));

		mockServer.when(getHoldById(holdId))
			.respond(response);
	}

	private HttpRequest getHoldById(String holdId) {
		return sierraMockServerRequests.get("/holds/" + holdId);
	}

	public void mockDeleteHold(String holdId) {
		deleteHoldById(holdId, sierraMockServerResponses.noContent());
	}

	public void mockDeleteHoldError(String holdId) {
		deleteHoldById(holdId, sierraMockServerResponses.badRequestError());
	}

	public void verifyDeleteHoldRequestMade(String holdId) {
		mockServer.verify(deleteHoldRequest(holdId));
	}

	public void verifyNoDeleteHoldRequestMade(String holdId) {
		mockServer.verify(deleteHoldRequest(holdId), never());
	}

	public void verifyNoDeleteHoldRequestMade() {
		mockServer.verify(deleteHoldRequest(), never());
	}

	private void deleteHoldById(String holdId, HttpResponse response) {
		mockServer.clear(deleteHoldRequest(holdId));

		mockServer.when(deleteHoldRequest(holdId))
			.respond(response);
	}

	private HttpRequest deleteHoldRequest(String holdId) {
		return sierraMockServerRequests.delete("/holds/" + holdId);
	}

	private HttpRequest deleteHoldRequest() {
		return sierraMockServerRequests.delete("/holds/*");
	}

	public void postPatronResponse(String uniqueId, int returnId) {
		mockServer
			.when(postPatronRequest(uniqueId))
			.respond(patronPlacedResponse(returnId));
	}

	public void postPatronErrorResponse(String uniqueId) {
		mockServer
			.when(postPatronRequest(uniqueId))
			.respond(sierraMockServerResponses.badRequestError());
	}

	public void mockRenewalSuccess(String checkoutID) {
		final var request = postRenewal(checkoutID);

		mockServer.clear(request);

		mockServer
			.when(request)
			.respond(sierraMockServerResponses
				.jsonSuccess("items/sierra-api-renewal-success.json"));
	}

	public void mockRenewalNoRecordsFound(String checkoutID) {
		final var request = postRenewal(checkoutID);

		mockServer.clear(request);

		mockServer
			.when(request)
			.respond(sierraMockServerResponses.noRecordsFound());
	}

	public void verifyRenewalRequestMade(String checkoutId) {
		mockServer.verify(postRenewal(checkoutId), once());
	}

	public void verifyNoCheckoutRelatedRequestsMade() {
		// This is a compromise because mock server seems to only support wildcards at the end of the path
		mockServer.verify(sierraMockServerRequests.post("/checkouts/*"), never());
	}

	public void thisRecordIsNotAvailableResponse(String patronId, String expectedRecordType) {
		mockServer
			.when(postPatronHoldRequest(patronId, PatronHoldPost.builder()
				.recordType(expectedRecordType)
				.recordNumber(null)
				.build()))
			.respond(sierraMockServerResponses.thisRecordIsNotAvailable());
	}

	public void verifyCreatePatronRequestMade(String uniqueId) {
		mockServer.verify(postPatronRequest(uniqueId), once());
	}

	public void verifyCreatePatronRequestNotMade(String uniqueId) {
		mockServer.verify(postPatronRequest(uniqueId), never());
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
		mockServer.verify(checkOutItemToPatron(itemBarcode, patronBarcode, pin), once());
	}

	public void checkOutItemToPatron(String itemBarcode, String patronBarcode) {
		mockServer
			.when(checkOutItemToPatron(itemBarcode, patronBarcode, null))
			.respond(checkedOutItemToPatron());
	}

	private HttpRequest checkOutItemToPatron(String itemBarcode, String patronBarcode, String pin) {
		final var checkoutPatch = CheckoutPatch.builder()
			.itemBarcode(itemBarcode)
			.patronBarcode(patronBarcode)
			.patronPin(pin)
			.build();

		return sierraMockServerRequests.post("/checkout", checkoutPatch);
	}

	public void getPatronByLocalIdSuccessResponse(String id, Patron patron) {
		mockServer
			.when(getPatron(id))
			.respond(successfulPatron(patron));
	}

	public void verifyGetPatronByLocalIdRequestMade(String id) {
		mockServer
			.verify(sierraMockServerRequests.get("/" + id)
				// Strictly expect the fields parameter because
				// a field not being listed can quietly lead to unintended
				// behaviour when later code tries to use a field that will never be provided
				.withQueryStringParameter("fields",
					"id,updatedDate,createdDate,expirationDate,names,barcodes,patronType,homeLibraryCode,emails,message,uniqueIds,emails,fixedFields,blockInfo,autoBlockInfo,deleted"),
				once());
	}

	public void noRecordsFoundWhenGettingPatronByLocalId(String patronId) {
		mockServer
			.when(getPatron(patronId))
			.respond(sierraMockServerResponses.noRecordsFound());
	}

	public void badRequestWhenGettingPatronByLocalId(String patronId) {
		mockServer
			.when(getPatron(patronId))
			.respond(sierraMockServerResponses.badRequestError());
	}

	public void updatePatron(String patronId) {
		mockServer
			.when(putPatron(patronId))
			.respond(sierraMockServerResponses.noContent());
	}

	public void verifyUpdatePatronRequestMade(String expectedPatronId) {
		verifyUpdatePatronRequest(expectedPatronId, once());
	}

	public void verifyUpdatePatronRequestNotMade(String expectedPatronId) {
		verifyUpdatePatronRequest(expectedPatronId, never());
	}

	private void verifyUpdatePatronRequest(String expectedPatronId, VerificationTimes times) {
		final var updateRequest = putPatron(expectedPatronId);

		log.info("Update patron requests recorded: {}",
			asList(mockServer.retrieveRecordedRequests(updateRequest)));

		mockServer.verify(updateRequest, times);
	}

	private RequestDefinition putPatron(String patronId) {
		return sierraMockServerRequests.put("/" + patronId);
	}

	public void patronsQueryFoundResponse(String uniqueId, String localPatronId) {

		final var queryEntry = buildPatronQuery(uniqueId);

		mockServer
			.when(patronsQuery(queryEntry))
			.respond(successfulPatronsQuery(localPatronId));
	}

	public void verifyPatronQueryRequestMade(String uniqueId) {

		final var queryEntry = buildPatronQuery(uniqueId);

		mockServer.verify(patronsQuery(queryEntry), once());
	}

	public void patronsQueryNotFoundResponse(String uniqueId) {

		final var queryEntry = buildPatronQuery(uniqueId);

		mockServer
			.when(patronsQuery(queryEntry))
			.respond(unsuccessfulPatronsQuery());
	}

	public void patronFoundResponse(String tag, String uniqueId, Patron patron) {
		mockServer
			.when(findPatron(tag, uniqueId))
			.respond(successfulPatron(patron));
	}

	public void patronNotFoundResponse(String tag, String content) {
		mockServer
			.when(findPatron(tag, content))
			.respond(sierraMockServerResponses.noRecordsFound());
	}

	public void verifyFindPatronRequestMade(String expectedVarFieldContent) {
		mockServer.verify(findPatron("u", expectedVarFieldContent), once());
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

		mockServer
			.when(postPatronHoldRequest(patronId, PatronHoldPost.builder()
				.recordType(expectedRecordType)
				.recordNumber(expectedRecordNumber)
				.build()))
			.respond(sierraMockServerResponses.noContent());
	}

	public void patronHoldRequestErrorResponse(String patronId, String expectedRecordType) {
		mockServer
			.when(postPatronHoldRequest(patronId, PatronHoldPost.builder()
				.recordType(expectedRecordType)
				.recordNumber(null)
				.build()))
			.respond(sierraMockServerResponses.serverError());
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
		mockServer
			.when(getPatronHolds(patronId), Times.once())
			.respond(patronHoldFoundResponse());
	}

	public void mockGetHoldsForPatronReturningSingleItemHold(String patronId,
		String holdIdUrl, String note, String itemId) {

		final var phre = List.of(
			PatronHoldResponse.builder()
				.id(holdIdUrl)
				.recordType("i")
				.record("https://some/record/" + itemId)
				.patron("https://some/patron/" + patronId)
				.frozen(false)
				.notNeededAfterDate("2023-09-01")
				.notWantedBeforeDate("2023-08-01")
				.status(SierraCodeNameTuple.builder()
					.code("0")
					.name("On hold")
					.build())
				.pickupLocation(SierraCodeNameTuple.builder()
					.code("21")
					.name("Vineland Branch")
					.build())
				.note(note)
				.build()
		);

		final var phr = PatronHoldsResponse.builder()
			.total(phre.size())
			.start(0)
			.entries(phre)
			.build();

		mockServer
			.when(getPatronHolds(patronId), Times.once())
			.respond(sierraMockServerResponses.jsonSuccess(json(phr)));
	}

	public void mockGetHoldsForPatronReturningSingleBibHold(String patronId,
		String holdIdUrl, String note, String bibId) {

		final var phre = List.of(
			PatronHoldResponse.builder()
				.id(holdIdUrl)
				.recordType("b")
				.record("https://some/record/" + bibId)
				.patron("https://some/patron/" + patronId)
				.frozen(false)
				.notNeededAfterDate("2023-09-01")
				.notWantedBeforeDate("2023-08-01")
				.status(SierraCodeNameTuple.builder()
					.code("0")
					.name("On hold")
					.build())
				.pickupLocation(SierraCodeNameTuple.builder()
					.code("21")
					.name("Vineland Branch")
					.build())
				.note(note)
				.build()
		);

		final var phr = PatronHoldsResponse.builder()
			.total(phre.size())
			.start(0)
			.entries(phre)
			.build();

		mockServer
			.when(getPatronHolds(patronId), Times.once())
			.respond(sierraMockServerResponses.jsonSuccess(json(phr)));
	}

	public void notFoundWhenGettingPatronRequests(String patronId) {
		mockServer
			.when(getPatronHolds(patronId))
			.respond(sierraMockServerResponses.noRecordsFound());
	}

	public void patronHoldNotFoundErrorResponse(String id) {
		mockServer
			.when(getPatronHolds(id))
			.respond(sierraMockServerResponses.noRecordsFound());
	}

	public void patronHoldErrorResponse(String id) {
		mockServer
			.when(getPatronHolds(id))
			.respond(sierraMockServerResponses.badRequestError());
	}

	public void addPatronGetExpectation(String patronId) {
		mockServer.when(getPatron(patronId))
			.respond(sierraPatronRecord(patronId));
	}

	private HttpResponse successfulPatronsQuery(String localPatronId) {

		final var queryResultSet = QueryResultSet.builder()
			.total(1)
			.start(0)
			.entries(Collections.singletonList(
				LinkResult.builder().link("https://sandbox.iii.com/iii/sierra-api/v6/patrons/"+localPatronId).build()
			))
			.build();

		return sierraMockServerResponses.jsonSuccess(json(queryResultSet));
	}
	private HttpResponse unsuccessfulPatronsQuery() {

//		{
//			"total": 0,
//			"start": 0,
//			"entries": []
//		}
		final var queryResultSet = QueryResultSet.builder()
			.total(0)
			.start(0)
			.entries(List.of())
			.build();

		return sierraMockServerResponses.jsonSuccess(json(queryResultSet));
	}

	private HttpResponse successfulPatron(Patron patron) {
		return sierraMockServerResponses.jsonSuccess(json(patron));
	}

	private HttpResponse patronPlacedResponse(int patronId) {
		return sierraMockServerResponses
			.jsonLink("https://sandbox.iii.com/iii/sierra-api/v6/patrons/" + patronId);
	}

	private HttpResponse checkedOutItemToPatron() {
		return sierraMockServerResponses
			.jsonLink("https://sandbox.iii.com/iii/sierra-api/v6/patrons/checkout/testPatronId");
	}

	private HttpResponse patronHoldFoundResponse() {
		return sierraMockServerResponses.jsonSuccess("patrons/sierra-api-patron-hold.json");
	}

	private HttpRequest getPatronHolds(String patronId) {
		return sierraMockServerRequests.get("/" + patronId + "/holds");
	}

	private HttpRequest getPatron(String patronId) {
		return sierraMockServerRequests.get("/" + patronId);
	}

	private HttpResponse sierraPatronRecord(String patronId) {
		return sierraMockServerResponses.jsonSuccess("patrons/patron/"+ patronId +".json");
	}

	@Data
	@Serdeable
	@Builder
	public static class Patron {
		Integer id;
		Integer patronType;
		String homeLibraryCode;
		List<String> barcodes;
		List<String> names;
		Boolean deleted;
		PatronBlock blockInfo;
		PatronBlock autoBlockInfo;
	}

	@Data
	@Serdeable
	@Builder
	public static class PatronBlock {
		String code;
	}

	@Data
	@Serdeable
	@Builder
	public static class PatronPatch {
		List<String> uniqueIds;
	}

	@Data
	@Serdeable
	@Builder
	public static class PatronHoldPost {
		String recordType;
		Integer recordNumber;
		String pickupLocation;
		String note;
	}

	@Data
	@Serdeable
	@Builder
	public static class SierraCodeNameTuple {
		String code;
		String name;
	}

	@Data
	@Serdeable
	@Builder
	public static class PatronHoldResponse {
		String id;
		String record;
		String patron;
		boolean frozen;
		String placed;
		String notNeededAfterDate;
		String notWantedBeforeDate;
		String recordType;
		String priority;
		String note;
		SierraCodeNameTuple pickupLocation;
		SierraCodeNameTuple status;
	}

	@Data
	@Serdeable
	@Builder
	public static class PatronHoldsResponse {
		int total;
		int start;
		List<PatronHoldResponse> entries;
	}
}
