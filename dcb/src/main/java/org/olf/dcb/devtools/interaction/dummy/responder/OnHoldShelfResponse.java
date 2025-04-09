package org.olf.dcb.devtools.interaction.dummy.responder;

import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import static org.olf.dcb.core.interaction.HostLmsItem.*;
import static org.olf.dcb.devtools.interaction.dummy.responder.DummyConstants.*;
import static org.olf.dcb.devtools.interaction.dummy.responder.DummyConstants.RET_PUA_RESPONSE_TYPE;

@Slf4j
@Singleton
public class OnHoldShelfResponse implements LmsResponseStrategy {

	private Map<String, Map<String, Function<String, Map<String, Object>>>> responseMap;

	/**
	 * Providing expected responses for the patron request workflows.
	 * should get us by the majority of use cases without needing to provide custom responses
	 */
	public Map<String, Map<String, Function<String, Map<String, Object>>>> buildResponses() {

		Map<String, Object> onHoldShelfResponse = Map.of(
			"itemStatus", ITEM_ON_HOLDSHELF,
			"rawItemStatus", ITEM_ON_HOLDSHELF,
			"requestStatus", "CONFIRMED",
			"rawRequestStatus", "CONFIRMED"
		);

		Map<String, Object> receivedResponse = Map.of(
			"itemStatus", ITEM_RECEIVED,
			"rawItemStatus", ITEM_RECEIVED,
			"requestStatus", "CONFIRMED",
			"rawRequestStatus", "CONFIRMED"
		);

		responseMap = new HashMap<>();

		Map<String, Function<String, Map<String, Object>>> stdResponse = new HashMap<>();
		stdResponse.put(SUPPLIER_ROLE, state -> receivedResponse);
		stdResponse.put(BORROWER_ROLE, state -> onHoldShelfResponse);

		Map<String, Function<String, Map<String, Object>>> puaResponse = new HashMap<>();
		puaResponse.put(SUPPLIER_ROLE, state -> receivedResponse);
		puaResponse.put(BORROWER_ROLE, state -> receivedResponse);
		puaResponse.put(PICKUP_ROLE, state -> onHoldShelfResponse);

		responseMap.put(RET_STD_RESPONSE_TYPE, stdResponse);
		responseMap.put(RET_PUA_RESPONSE_TYPE, puaResponse);

		return responseMap;
	}

	@Override
	public boolean supports(String state, String role, String responseType) {
		return "READY_FOR_PICKUP".equalsIgnoreCase(state);
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

		// Get appropriate handler for role or log warning if not found
		Function<String, Map<String, Object>> handler = handlers.get(role);

		if (handler == null) {
			log.warn("No handler found for responseType: {}, role: {}", responseType, role);
			// Fallback to default response
			return DummyResponse.builder().data(Map.of("itemStatus", "UNKNOWN", "requestStatus", "UNKNOWN")).build();
		}

		// Generate data using the handler
		Map<String, Object> data = handler.apply(state);

		final var response = DummyResponse.builder().data(data).build();
		log.debug("response: {}", response);
		return response;
	}
}
