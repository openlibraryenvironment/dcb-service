package org.olf.reshare.dcb.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;

import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.olf.reshare.dcb.storage.PatronRequestRepository;
import org.olf.reshare.dcb.test.DcbTest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

	public static final Logger log = LoggerFactory.getLogger(PatronRequestTest.class);
	@Inject
	@Client("/")
	HttpClient client;

	@Inject
	PatronRequestRepository requestRepository;

	@Test
	@Order(1)
	void testPlacePatronRequestValidation() {
		log.info("Empty patron request");

		final var e = assertThrows(HttpClientResponseException.class, () -> {
			client.toBlocking()
				.exchange(HttpRequest.POST("/patrons/requests/place", new JSONObject()));
		});

		HttpResponse<?> response = e.getResponse();
		assertEquals(HttpStatus.BAD_REQUEST, response.getStatus());

		final var patronRequest = new JSONObject() {{
			// citation
			JSONObject citation = new JSONObject() {{
				put("bibClusterId", UUID.randomUUID().toString());
			}};
			put("citation", citation);
			// requestor
			JSONObject requestor = new JSONObject() {{
				put("identifier", "jane-smith");
				JSONObject agency = new JSONObject() {{
					put("code", "RGX12");
				}};
				put("agency", agency);
			}};
			put("requestor", requestor);
			// pickup location
			JSONObject pickupLocation = new JSONObject() {{
				put("code", "ABC123");
			}};
			put("pickupLocation", pickupLocation);
		}};

		log.debug("Using payload: {}", patronRequest);

		final var request = client.toBlocking()
			.exchange(HttpRequest.POST("/patrons/requests/place", patronRequest));

		assertEquals(HttpStatus.OK, request.getStatus());
	}

	@Test
	@Order(2)
	void testPatronRequestCreation() {
		log.debug("1. testPatronRequestCreation");
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

		log.debug("Using payload: {}", patronRequest);

		// make a request to the api
		client.toBlocking()
			.exchange(HttpRequest.POST("/patrons/requests/place", patronRequest));

		log.debug("Created test patron request: {}", patronRequest);

		// Make sure that we have 1 task
		final var patronRequests = Flux.from(
			requestRepository.findAll()).collectList().block();

		assertNotNull(patronRequests);

		// Second request do should be 2 patron request present
		log.debug("Got {} patron request: {}", patronRequests.size(),
			patronRequests);

		assertEquals(2, patronRequests.size());
	}

}
