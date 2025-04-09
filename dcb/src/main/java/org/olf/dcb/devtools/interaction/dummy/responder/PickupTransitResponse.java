package org.olf.dcb.devtools.interaction.dummy.responder;

import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import static org.olf.dcb.core.interaction.HostLmsItem.ITEM_TRANSIT;
import static org.olf.dcb.devtools.interaction.dummy.responder.DummyConstants.*;

@Slf4j
@Singleton
public class PickupTransitResponse implements LmsResponseStrategy {

	private Map<String, Map<String, Function<String, Map<String, Object>>>> responseMap;

	/**
	 * Providing expected responses for the patron request workflows.
	 * should get us by the majority of use cases without needing to provide custom responses
	 */
	public Map<String, Map<String, Function<String, Map<String, Object>>>> buildResponses() {
		responseMap = new HashMap<>();

		// Initialize response handlers
		Map<String, Function<String, Map<String, Object>>> response = new HashMap<>();
		response.put(SUPPLIER_ROLE, state -> Map.of("itemStatus", ITEM_TRANSIT, "requestStatus", "CONFIRMED"));
		response.put(BORROWER_ROLE, state -> Map.of("itemStatus", "PICKUP_TRANSIT", "requestStatus", "CONFIRMED"));
		response.put(PICKUP_ROLE, state -> Map.of("itemStatus", "PICKUP_TRANSIT", "requestStatus", "CONFIRMED"));

		// Both the standard and PUA workflow are waiting for the supplier to put the item in transit
		responseMap.put(RET_STD_RESPONSE_TYPE, response);
		responseMap.put(RET_PUA_RESPONSE_TYPE, response);

		return responseMap;
	}

	@Override
	public boolean supports(String state, String role, String responseType) {
		return "PICKUP_TRANSIT".equalsIgnoreCase(state);
	}

	@Override
	public DummyResponse getResponse(String state, String role, String responseType) {

		if (Objects.equals(responseType, CUSTOM_RESPONSE_TYPE)) {
			return FileResponseProvider.getResponse(state, role);
		}

		buildResponses();

		// Get handler map for response type
		Map<String, Function<String, Map<String, Object>>> handlers =
			responseMap.getOrDefault(responseType, new HashMap<>());

		// Get appropriate handler for role
		Function<String, Map<String, Object>> handler = handlers.get(role);

		if (handler == null) {
			log.warn("No handler found for responseType: {}, role: {}", responseType, role);
			// Fallback to default response
			return DummyResponse.builder().data(Map.of("itemStatus", "UNKNOWN", "requestStatus", "UNKNOWN")).build();
		}

		// Generate data using the handler
		Map<String, Object> data = handler.apply(state);

		DummyResponse response = DummyResponse.builder().data(data).build();
		log.debug("response: {}", response);
		return response;
	}
}
