package org.olf.reshare.dcb.api;

import static io.micronaut.http.HttpStatus.BAD_REQUEST;
import static io.micronaut.http.HttpStatus.OK;
import static java.util.Objects.requireNonNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.olf.reshare.dcb.core.model.PatronRequest;
import org.olf.reshare.dcb.storage.PatronRequestRepository;
import org.olf.reshare.dcb.test.DcbTest;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.inject.Inject;
import net.minidev.json.JSONObject;
import reactor.core.publisher.Flux;

@TestMethodOrder(OrderAnnotation.class)
@DcbTest
class PatronRequestTests {
	@Inject
	@Client("/")
	HttpClient client;

	@Inject
	PatronRequestRepository requestRepository;

	@BeforeEach
	void beforeEach() {
		final var allPatronRequests = getAllPatronRequests();

		allPatronRequests.forEach(
			patronRequest -> requestRepository.delete(patronRequest.getId()));
	}

	@Test
	void cannotPlaceRequestWhenNoInformationIsProvided() {
		// These are separate variables to only have single invocation in assertThrows
		final var blockingClient = client.toBlocking();
		final var request = HttpRequest.POST("/patrons/requests/place",
			new JSONObject());

		final var e = assertThrows(HttpClientResponseException.class,
			() -> blockingClient.exchange(request));

		final var response = e.getResponse();

		assertEquals(BAD_REQUEST, response.getStatus());
	}

	@Test
	void canPlaceAPatronRequest() {
		final var response = placePatronRequest(createPlacePatronRequestCommand());

		assertEquals(OK, response.getStatus());

		final var patronRequests = getAllPatronRequests();

		assertNotNull(patronRequests);
		assertEquals(1, patronRequests.size());
	}

	@Test
	void canGetAPlacedPatronRequestViaAdminAPI() {
		final var placeResponse = placePatronRequest(createPlacePatronRequestCommand());

		assertEquals(OK, placeResponse.getStatus());
		assertNotNull(placeResponse.body());

		final var id = requireNonNull(placeResponse.body()).id();

		var getResponse = client.toBlocking()
			.retrieve(HttpRequest.GET("/admin/patrons/requests/" + id),
				PlacedPatronRequest.class);

		assertEquals(id, getResponse.id());
	}

	private static JSONObject createPlacePatronRequestCommand() {
		return new JSONObject() {{
			// citation
			final var citation = new JSONObject() {{
				put("bibClusterId", UUID.randomUUID().toString());
			}};
			put("citation", citation);
			// requestor
			final var requestor = new JSONObject() {{
				put("identifier", "jane-smith");
				final var agency = new JSONObject() {{
					put("code", "RGX12");
				}};
				put("agency", agency);
			}};
			put("requestor", requestor);
			// pickup location
			final var pickupLocation = new JSONObject() {{
				put("code", "ABC123");
			}};
			put("pickupLocation", pickupLocation);
		}};
	}

	private HttpResponse<PlacedPatronRequest> placePatronRequest(JSONObject json) {
		return client.toBlocking()
			.exchange(HttpRequest.POST("/patrons/requests/place", json),
				PlacedPatronRequest.class);
	}

	private List<PatronRequest> getAllPatronRequests() {
		return Flux.from(requestRepository.findAll()).collectList().block();
	}

	@Serdeable
	public record PlacedPatronRequest(UUID id) { }
}
