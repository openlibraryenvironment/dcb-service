package org.olf.reshare.dcb.core.interaction.sierra;

import java.util.List;

import org.mockserver.client.MockServerClient;
import org.mockserver.model.JsonBody;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import static org.mockserver.model.MediaType.APPLICATION_JSON;

import io.micronaut.core.io.ResourceLoader;
import io.micronaut.serde.annotation.Serdeable;
import lombok.Builder;
import lombok.Data;

public class SierraPatronsAPIFixture {
	private final MockServerClient mockServer;
	private final SierraMockServerRequests sierraMockServerRequests;
	private final SierraMockServerResponses sierraMockServerResponses;
	
	public SierraPatronsAPIFixture(MockServerClient mockServer, ResourceLoader loader) {
		this.mockServer = mockServer;
		this.sierraMockServerRequests = new SierraMockServerRequests(
			"/iii/sierra-api/v6/patrons");

		this.sierraMockServerResponses = new SierraMockServerResponses(
			"classpath:mock-responses/sierra/", loader);
	}

	public void postPatronResponse(String uniqueId, int returnId) {
		mockServer
			.when(postPatronRequest(uniqueId))
			.respond(patronPlacedResponse(returnId));
	}

	public void postPatronErrorResponse(String uniqueId) {
		mockServer
			.when(postPatronRequest(uniqueId))
			.respond(patronErrorResponse());
	}

	public void patronResponseForUniqueId(String uniqueId) {
		mockServer
			.when(getPatronFindRequest(uniqueId))
			.respond(patronFoundResponse());
	}

	public void patronNotFoundResponseForUniqueId(String uniqueId) {
		mockServer
			.when(getPatronFindRequest(uniqueId))
			.respond(patronNotFoundResponse());
	}

	public void patronHoldRequestResponse(String id, Integer recordNumber,
		String pickupLocation) {

		mockServer
			.when(createPatronHoldRequest(id, recordNumber, pickupLocation))
			.respond(sierraMockServerResponses.noContent());
	}

	public void patronHoldRequestErrorResponse(String id, Integer recordNumber,
		String pickupLocation) {

		mockServer
			.when(createPatronHoldRequest(id, recordNumber, pickupLocation))
			.respond(patronErrorResponse());
	}

	public void patronHoldResponse(String id) {
		// Change this to dyamically build the response so it contains the patronRequestID in the notes field

		mockServer
			.when(sierraMockServerRequests.get("/" + id + "/holds"))
			.respond(patronHoldFoundResponse());
	}


	public void patronHoldResponse(String id, String note) {
		List<PatronHoldResponse> phr = new java.util.ArrayList();
		phr.add(PatronHoldResponse.builder()
                        .id(id)
                	.record("https://some/record/id")
                	.patron("https://some/patron/id")
                	.frozen(false)
                	// .placed()
                	.notNeededAfterDate("2023-09-01")
                	.notWantedBeforeDate("2023-08-01")
                	.recordType("i")
                	// .priority()
                	.note(note)
                        .build());

		mockServer
			.when(sierraMockServerRequests.get("/" + id + "/holds"))
			.respond(HttpResponse.response().withContentType(APPLICATION_JSON).withBody(JsonBody.json(phr)));
	}

	public void patronHoldErrorResponse(String id) {
		mockServer
			.when(sierraMockServerRequests.get("/" + id + "/holds"))
			.respond(patronErrorResponse());
	}

	public void addPatronGetExpectation(Long localId) {
		mockServer.when(getPatronRequest(localId))
			.respond(sierraPatronRecord(localId));
	}

	private HttpResponse patronNotFoundResponse() {
		return sierraMockServerResponses.notFound("patrons/sierra-api-patron-not-found.json");
	}

	private HttpResponse patronFoundResponse() {
		return sierraMockServerResponses.jsonSuccess("patrons/sierra-api-patron-found.json");
	}

	private HttpResponse patronErrorResponse() {
		return sierraMockServerResponses.textError();
	}

	private HttpResponse patronPlacedResponse(int returnId) {
		return sierraMockServerResponses
			.jsonLink("https://sandbox.iii.com/iii/sierra-api/v6/patrons/" + returnId);
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

	private HttpRequest getPatronFindRequest(String uniqueId) {
		return sierraMockServerRequests.get("/find")
			.withQueryStringParameter("varFieldTag", "u")
			.withQueryStringParameter("varFieldContent", uniqueId);
	}

	private HttpRequest createPatronHoldRequest(String id, Integer recordNumber,
		String pickupLocation) {

		final var body = PatronHoldPost.builder()
			.recordType("i")
			.recordNumber(recordNumber)
			.pickupLocation(pickupLocation)
			.build();

		return sierraMockServerRequests.post(requestsPath(id), body);
	}

	private static String requestsPath(String id) {
		return "/" + id + "/holds/requests";
	}

	private HttpRequest getPatronRequest(Long id) {
		return sierraMockServerRequests.get("/" + id);
	}

	private HttpResponse sierraPatronRecord(Long id) {
		return sierraMockServerResponses.jsonSuccess("patrons/patron/"+ id +".json");
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
                // "pickupLocation": {
                // "code": "21",
                // "name": "Vineland Branch"
                // },
                // "status": {
                // "code": "0",
                // "name": "on hold."
                // },
	}

}
