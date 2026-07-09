package org.olf.dcb.core.interaction.foundation.customisations;


import com.fasterxml.jackson.databind.ObjectMapper;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.HttpClient;
import jakarta.inject.Named;
import org.olf.dcb.core.interaction.foundation.customisations.evergreen.dto.EvergreenEvent;
import org.olf.dcb.core.interaction.foundation.customisations.evergreen.dto.EvergreenResponse;
import org.olf.dcb.core.interaction.foundation.customisations.evergreen.payloads.payloads.EvergreenRenewalPayload;
import org.olf.dcb.core.interaction.foundation.strategies.CirculationStrategy;
import org.olf.dcb.core.interaction.foundation.NcipAdaptor;
import org.olf.dcb.core.interaction.*;
import org.olf.dcb.core.model.HostLms;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.util.List;
import java.util.Map;

// Use case: 90% covered by NCIP, this is the rest
@Bean
@Named("EvergreenExampleCustomOverride") // Must match what is in the client config, so we know to override for this op
public class EvergreenExampleCustomOverride implements CirculationStrategy {

	private final NcipAdaptor defaultAdapter; // Delegate for stuff that works
	private final HttpClient httpClient;      // Client for the custom workaround
	private final String evergreenApiUrl; // need the api url as well

	public EvergreenExampleCustomOverride(@Parameter("hostLms") HostLms lms,
															HttpClient httpClient, ObjectMapper objectMapper) {
		//
		this.defaultAdapter = new NcipAdaptor(lms, httpClient);
		this.httpClient = httpClient;
		this.objectMapper = objectMapper;
		this.evergreenApiUrl = (String) lms.getClientConfig().get("evergreen-api-url");
	}

	private final ObjectMapper objectMapper; // Inject this in constructor

	private HostLmsRenewal validateEvergreenResponse(String jsonResponse, HostLmsRenewal renewal) {
		try {
			// OpenSRF returns an array of results, usually [ { "payload": ... } ]
			// We parse specifically for the "Event" object structure
			EvergreenResponse[] responses = objectMapper.readValue(jsonResponse, EvergreenResponse[].class);

			if (responses == null || responses.length == 0) {
				throw new RuntimeException("Empty response from Evergreen API");
			}

			EvergreenEvent event = responses[0].getContent();

			// ID 0 represents SUCCESS in standard Evergreen/OpenILS
			if (event.getId() == 0 || "SUCCESS".equalsIgnoreCase(event.getTextCode())) {

				// Just here for an example

//				// Extract the new due date if available in the payload
//				if (event.getPayload() != null && event.getPayload().getDueDate() != null) {
//					// You might need a formatter here depending on the exact date string format
//					// renewal.setDueDate(LocalDate.parse(event.getPayload().getDueDate()));
//				}

				return renewal;
			}

			// Handle common failures
			if ("OPEN_CIRCULATION_EXISTS".equals(event.getTextCode())) {
				throw new RuntimeException("Item is already checked out and cannot be renewed (Policy Failure).");
			}

			throw new RuntimeException("Evergreen Renewal Failed: " + event.getTextCode() + " - " + event.getDesc());

		} catch (IOException e) {
			throw new RuntimeException("Failed to parse Evergreen response", e);
		}
	}

	// Example of customisation
	// i.e. renewals might not work in NCIP for Evergreen
	// So we write it here
	@Override
	public Mono<HostLmsRenewal> renew(HostLmsRenewal renewal) {
		// 1. Build the args object
		EvergreenRenewalPayload args = EvergreenRenewalPayload.builder()
			.itemBarcode(renewal.getLocalItemBarcode())
			.patronBarcode(renewal.getLocalPatronBarcode())
			.build();

		// 2. Wrap it in the OpenSRF Protocol format: [ "AUTH_TOKEN", { ...args... } ]
		// Note: In a real app, you would fetch a valid auth token from a config or cache.
		String authToken = "AuthedUserToken";
		List<Object> osrfParams = List.of(authToken, args);

		// 3. Construct the Form Data for the OpenSRF Gateway
		// The gateway expects form-url-encoded parameters: service, method, and param
		return Mono.from(httpClient.retrieve(
			HttpRequest.POST(evergreenApiUrl + "/osrf-gateway-v1",
				Map.of(
					"service", "open-ils.circ",
					"method", "open-ils.circ.renew",
					"param", osrfParams // Micronaut will JSON-encode this list automatically
				)
			).contentType(MediaType.APPLICATION_FORM_URLENCODED)
		)).map(response -> {
			// 4. Parse the OpenSRF response (Payload -> Text -> ILS Event)
			// Evergreen returns an "Event" object (e.g., id:0 is SUCCESS, id:1234 is FAILURE)
			return validateEvergreenResponse(response, renewal);
		});
	}

	@Override
	public Mono<String> checkOutItem(CheckoutItemCommand command) {
		// NCIP checkout works fine, so just let the base adapter handle it
		return defaultAdapter.checkOutItem(command);
	}

	@Override
	public Mono<HostLmsItem> getItem(String localItemId) {
		return defaultAdapter.getItem(localItemId);
	}


}
