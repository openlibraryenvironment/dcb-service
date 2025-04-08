package org.olf.dcb.devtools.interaction.dummy;

import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

import static org.olf.dcb.core.interaction.HostLmsItem.*;
import static org.olf.dcb.devtools.interaction.dummy.DummyConstants.PICKUP_ROLE;
import static org.olf.dcb.devtools.interaction.dummy.DummyConstants.PUA_RESPONSE_TYPE;

@Slf4j
@Singleton
public class OnHoldShelfResponse implements LmsResponseStrategy {

	@Override
	public boolean supports(String state, String role, String responseType) {
		return "READY_FOR_PICKUP".equalsIgnoreCase(state) && PUA_RESPONSE_TYPE.equals(responseType);
	}

	@Override
	public DummyResponse getResponse(String state, String role, String responseType) {

		final var itemStatus = PICKUP_ROLE.equals(role) ? ITEM_ON_HOLDSHELF : ITEM_RECEIVED;
		final var rawItemStatus = PICKUP_ROLE.equals(role) ? ITEM_ON_HOLDSHELF : ITEM_RECEIVED;
		final var requestStatus = "CONFIRMED";
		final var rawRequestStatus = "CONFIRMED";

		// The item is on hold shelf
		// see; HandleBorrowerItemOnHoldShelf transition
		final Map<String, Object> data = Map.of(
			"itemStatus", itemStatus,
			"rawItemStatus", rawItemStatus,
			"requestStatus", requestStatus,
			"rawRequestStatus", rawRequestStatus);
		final var response = DummyResponse.builder().data(data).build();

		log.debug("response: {}", response);
		return response;
	}



}
