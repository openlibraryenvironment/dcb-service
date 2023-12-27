package org.olf.dcb.core.interaction.sierra;

import static java.util.Arrays.asList;
import static org.mockserver.model.JsonBody.json;
import static org.mockserver.verify.VerificationTimes.never;
import static org.mockserver.verify.VerificationTimes.once;

import java.util.ArrayList;
import java.util.List;

import org.mockserver.client.MockServerClient;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.JsonBody;
import org.mockserver.model.RequestDefinition;
import org.mockserver.verify.VerificationTimes;
import org.olf.dcb.test.TestResourceLoaderProvider;

import io.micronaut.serde.annotation.Serdeable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

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

	public void getHoldById404(String holdId) {
		mockServer
			.when(sierraMockServerRequests.get("/holds/"+holdId))
			.respond(sierraMockServerResponses.noRecordsFound());
	}

	public void getHoldById(String holdId) {
		mockServer.clear(sierraMockServerRequests.get("/holds/"+holdId));
		mockServer
			.when(sierraMockServerRequests.get("/holds/"+holdId))
			.respond(sierraMockServerResponses.jsonSuccess("holds/11987.json"));
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

	public void getPatronByLocalIdSuccessResponse(String id) {
		mockServer
			.when(getPatron(id))
			.respond(successfulPatron());
	}

	public void getPatronByLocalIdWithoutPatronTypeSuccessResponse(String id) {
		mockServer
			.when(getPatron(id))
			.respond(sierraMockServerResponses.jsonSuccess(json(
				Patron.builder()
					.id(Integer.parseInt(id))
					.patronType(null)
					.homeLibraryCode("testccc")
					.barcodes(List.of("647647746"))
					.names(List.of("Bob"))
					.build())));
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
			.respond(successfulPatron());
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

	public void patronResponseForUniqueId(String tag, String content) {
		mockServer
			.when(findPatron(tag, content))
			.respond(patronFoundResponse());
	}

	public void patronResponseForUniqueIdExpectedPtype(String tag, String uniqueId) {
		mockServer
			.when(findPatron(tag, uniqueId))
			.respond(successfulPatron());
	}

	public void patronNotFoundResponseForUniqueId(String tag, String content) {
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

	public void patronHoldRequestResponse(String patronId,
		String expectedRecordType, Integer expectedRecordNumber) {

		mockServer
			.when(postPatronHoldRequest(patronId, expectedRecordType, expectedRecordNumber))
			.respond(sierraMockServerResponses.noContent());
	}

	public void patronHoldRequestErrorResponse(String patronId, String expectedRecordType) {
		mockServer
			.when(postPatronHoldRequest(patronId, expectedRecordType, null))
			.respond(sierraMockServerResponses.serverError());
	}

	public void patronHoldResponse(String id) {
		mockServer
			.when(getPatronHolds(id))
			.respond(patronHoldFoundResponse());
	}

	public void patronHoldResponse(String patronId, String holdIdUrl, String note) {
		List<PatronHoldResponse> phre = new ArrayList<>();
		phre.add(PatronHoldResponse.builder()
			.id(holdIdUrl)
			.record("https://some/record/" + patronId)
			.patron("https://some/patron/" + patronId)
			.frozen(false)
			.notNeededAfterDate("2023-09-01")
			.notWantedBeforeDate("2023-08-01")
			.recordType("i")
			.status(SierraCodeNameTuple.builder().code("0").name("On hold").build())
			.pickupLocation(SierraCodeNameTuple.builder().code("21").name("Vineland Branch").build())
			.note(note)
			.build());

		PatronHoldsResponse phr = PatronHoldsResponse.builder()
			.total(phre.size())
			.start(0)
			.entries(phre)
			.build();

		mockServer.clear(getPatronHolds(patronId));

		mockServer
			.when(getPatronHolds(patronId))
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

	private HttpResponse patronFoundResponse() {
		return sierraMockServerResponses.jsonSuccess("patrons/sierra-api-patron-found.json");
	}

	private HttpResponse successfulPatron() {
		return sierraMockServerResponses.jsonSuccess(examplePatron());
	}

	private HttpResponse patronPlacedResponse(int patronId) {
		return sierraMockServerResponses
			.jsonLink("https://sandbox.iii.com/iii/sierra-api/v6/patrons/" + patronId);
	}

	private HttpResponse patronHoldFoundResponse() {
		return sierraMockServerResponses.jsonSuccess("patrons/sierra-api-patron-hold.json");
	}

	private HttpRequest postPatronRequest(String uniqueId) {
		final var patronPatch = PatronPatch.builder()
			.uniqueIds(List.of(uniqueId))
			.build();

		return sierraMockServerRequests.post(patronPatch);
	}

	private HttpRequest postPatronHoldRequest(String patronId,
		String expectedRecordType, Integer expectedRecordNumber) {

		return sierraMockServerRequests.post("/" + patronId + "/holds/requests")
			.withBody(json(PatronHoldPost.builder()
				.recordType(expectedRecordType)
				.recordNumber(expectedRecordNumber)
				.build()));
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

	private static JsonBody examplePatron() {
		return json(
			Patron.builder()
				.id(1000002)
				.patronType(15)
				.homeLibraryCode("testccc")
				.barcodes(List.of("647647746"))
				.names(List.of("Bob"))
				.build());
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
