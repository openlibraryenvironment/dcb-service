package org.olf.reshare.dcb.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;

import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.olf.reshare.dcb.core.model.PatronRequest;
import org.olf.reshare.dcb.processing.PatronRequestRecord;
import org.olf.reshare.dcb.storage.PatronRequestRepository;
import org.olf.reshare.dcb.test.DcbTest;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import jakarta.inject.Inject;
import net.minidev.json.JSONObject;
import reactor.core.publisher.Flux;

@TestMethodOrder(OrderAnnotation.class)
@DcbTest
class PatronRequestTest {
	@Inject
	@Client("/")
	HttpClient client;

	@Inject
	PatronRequestRepository requestRepository;

	@Test
	@Order(1)
	void testPlacePatronRequestValidation() {
		final var e = assertThrows(HttpClientResponseException.class, () -> {
			client.toBlocking()
				.exchange(HttpRequest.POST("/patrons/requests/place", new JSONObject()));
		});

		HttpResponse<?> response = e.getResponse();

		assertEquals(HttpStatus.BAD_REQUEST, response.getStatus());
	}

	@Test
	@Order(2)
	void testPatronRequestCreation() {
		final var patronRequest = new JSONObject() {{
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

		// make a request to the api
		client.toBlocking()
			.exchange(HttpRequest.POST("/patrons/requests/place", patronRequest));

		final var patronRequests = Flux.from(
			requestRepository.findAll()).collectList().block();

		assertNotNull(patronRequests);
		assertEquals(1, patronRequests.size());
	}

	@Test
	@Order(3)
	void testGetPatronRequest() {
		final var patronRequests = Flux.from(requestRepository.findAll()).collectList().block();

		assertNotNull(patronRequests);
		assertEquals(1, patronRequests.size());

		// Get first request
		PatronRequest patronRequest = patronRequests.get(0);

		// get patron request by id via audit controller
		var response = client.toBlocking().retrieve(HttpRequest.GET("/admin/patrons/requests/"+patronRequest.getId()), PatronRequestRecord.class);

		// check the response has same id as the requested patron request
		assertEquals(response.id(), patronRequest.getId());
	}

}
