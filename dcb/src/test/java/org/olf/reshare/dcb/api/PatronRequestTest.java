package org.olf.reshare.dcb.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.olf.reshare.dcb.core.model.PatronRequest;
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
import net.minidev.json.JSONObject;
import org.olf.reshare.dcb.processing.PatronRequestRecord;
import jakarta.inject.Inject;
import reactor.core.publisher.Flux;
import java.util.List;
import java.util.UUID;

@DcbTest
class PatronRequestTest {

	public static final Logger log = LoggerFactory.getLogger(PatronRequestTest.class);
	@Inject
	@Client("/")
	HttpClient client;

	@Inject
	PatronRequestRepository requestRepository;

	@Test
	public void testPlacePatronRequestValidation() {

                log.info("Empty patron request");
		HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
			JSONObject patronRequest = new JSONObject();
			client.toBlocking().exchange(HttpRequest.POST("/patrons/requests/place", patronRequest));
		});

		HttpResponse<?> response = e.getResponse();
		assertEquals(HttpStatus.BAD_REQUEST, response.getStatus());

		JSONObject patronRequest = new JSONObject() {{
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

		log.debug("Using payload: {}", patronRequest.toString());

		HttpResponse<PatronRequestRecord> rp = client.toBlocking().exchange(HttpRequest.POST("/patrons/requests/place", patronRequest));
		assertEquals(HttpStatus.OK, rp.getStatus());
	}

	@Test
	void testPatronRequestCreation() {
		log.debug("1. testPatronRequestCreation");
		JSONObject patronRequest = new JSONObject() {{
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
		log.debug("Using payload: {}", patronRequest.toString());

		// make a request to the api
		client.toBlocking().exchange(HttpRequest.POST("/patrons/requests/place", patronRequest));
		log.debug("Created test patron request: {}", patronRequest);

		// Make sure that we have 1 task
		List<PatronRequest> patronRequests = Flux.from(requestRepository.findAll()).collectList().block();
		assert patronRequests != null;

		log.debug("Got {} patron request: {}", patronRequests.size(), patronRequests.toString());
		assert patronRequests.size() == 1;
	}

}
