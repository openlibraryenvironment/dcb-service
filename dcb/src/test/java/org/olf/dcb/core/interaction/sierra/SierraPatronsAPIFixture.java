package org.olf.dcb.core.interaction.sierra;

import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.MediaType.APPLICATION_JSON;

import java.util.ArrayList;
import java.util.List;

import org.mockserver.client.MockServerClient;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.JsonBody;

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

	public void getPatronByLocalId(String id) {
		mockServer
			.when(request()
				.withMethod("GET")
				.withPath("/iii/sierra-api/v6/patrons/" + id))
			.respond(patronUpdatedResponse());
	}

	public void noRecordsFoundWhenGettingPatronByLocalId(String patronId) {
		mockServer
			.when(request()
				.withMethod("GET")
				.withPath("/iii/sierra-api/v6/patrons/" + patronId))
			.respond(patronNotFoundResponse());
	}

	public void serverErrorWhenGettingPatronByLocalId(String patronId) {
		mockServer
			.when(request()
				.withMethod("GET")
				.withPath("/iii/sierra-api/v6/patrons/" + patronId))
			.respond(patronErrorResponse());
	}

	public void updatePatron(String id) {
		mockServer
			.when(request()
				.withMethod("PUT")
				.withPath("/iii/sierra-api/v6/patrons/" + id))
			.respond(patronUpdatedResponse());
	}

	public void patronResponseForUniqueId(String uniqueId) {
		mockServer
			.when(getPatronFindRequest(uniqueId))
			.respond(patronFoundResponse());
	}

	public void patronResponseForUniqueIdExpectedPtype(String uniqueId) {
		mockServer
			.when(getPatronFindRequest(uniqueId))
			.respond(patronUpdatedResponse());
	}

	public void patronNotFoundResponseForUniqueId(String uniqueId) {
		mockServer
			.when(getPatronFindRequest(uniqueId))
			.respond(patronNotFoundResponse());
	}

	public void patronHoldRequestResponse(String patronId) {
		mockServer
			.when(createPatronHoldRequest(patronId))
			.respond(sierraMockServerResponses.noContent());
	}

	public void patronHoldRequestErrorResponse(String patronId) {
		mockServer
			.when(createPatronHoldRequest(patronId))
			.respond(patronErrorResponse());
	}

	public void patronHoldResponse(String id) {
		mockServer
			.when(sierraMockServerRequests.get("/" + id + "/holds"))
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

		mockServer.clear(
			request()
				.withMethod("GET")
				.withPath("/iii/sierra-api/v6/patrons/" + patronId + "/holds")
		);

		mockServer
			.when(request()
				.withMethod("GET")
				.withPath("/iii/sierra-api/v6/patrons/" + patronId + "/holds"))
			.respond(HttpResponse.response().withContentType(APPLICATION_JSON).withBody(JsonBody.json(phr)));
	}

	public void notFoundWhenGettingPatronRequests(String patronId) {
		mockServer
			.when(request()
				.withMethod("GET")
				.withPath("/iii/sierra-api/v6/patrons/" + patronId + "/holds"))
			.respond(sierraMockServerResponses.notFound("patrons/sierra-api-patron-not-found.json"));
	}

	public void patronHoldNotFoundErrorResponse(String id) {
		mockServer
			.when(sierraMockServerRequests.get("/" + id + "/holds"))
			.respond(sierraMockServerResponses.notFound("patrons/sierra-api-patron-not-found.json"));
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

	private HttpResponse patronUpdatedResponse() {
		return sierraMockServerResponses.jsonSuccess("patrons/sierra-api-patron-updated.json");
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

	private HttpRequest createPatronHoldRequest(String patronId) {
		return sierraMockServerRequests.post("/" + patronId + "/holds/requests");
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
