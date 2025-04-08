package org.olf.dcb.devtools.interaction.dummy;

import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

import static org.olf.dcb.core.interaction.HostLmsItem.ITEM_TRANSIT;
import static org.olf.dcb.devtools.interaction.dummy.DummyConstants.PUA_RESPONSE_TYPE;
import static org.olf.dcb.devtools.interaction.dummy.DummyConstants.SUPPLIER_ROLE;

@Slf4j
@Singleton
public class PickupTransitResponse implements LmsResponseStrategy {

	@Override
	public boolean supports(String state, String role, String responseType) {
		return "PICKUP_TRANSIT".equalsIgnoreCase(state) && PUA_RESPONSE_TYPE.equals(responseType);
	}

	@Override
	public DummyResponse getResponse(String state, String role, String responseType) {

		final var itemStatus = SUPPLIER_ROLE.equals(role) ? ITEM_TRANSIT : "PICKUP_TRANSIT";
		final var requestStatus = "CONFIRMED";

		// should represent the supplier item in transit to the pickup location
		// see; HandleSupplierInTransit transition
		final Map<String, Object> data = Map.of("itemStatus", itemStatus, "requestStatus", requestStatus);
		final var response = DummyResponse.builder().data(data).build();

		log.debug("response: {}", response);
		return response;
	}



}
