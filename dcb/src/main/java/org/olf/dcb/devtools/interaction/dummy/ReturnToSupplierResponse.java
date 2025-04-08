package org.olf.dcb.devtools.interaction.dummy;

import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

import static org.olf.dcb.core.interaction.HostLmsItem.ITEM_AVAILABLE;
import static org.olf.dcb.core.interaction.HostLmsItem.ITEM_LOANED;
import static org.olf.dcb.core.interaction.HostLmsRequest.HOLD_MISSING;
import static org.olf.dcb.devtools.interaction.dummy.DummyConstants.*;

@Slf4j
@Singleton
public class ReturnToSupplierResponse implements LmsResponseStrategy {

	@Override
	public boolean supports(String state, String role, String responseType) {
		return "COMPLETED".equalsIgnoreCase(state) && PUA_RESPONSE_TYPE.equals(responseType);
	}

	@Override
	public DummyResponse getResponse(String state, String role, String responseType) {

		final var itemStatus = SUPPLIER_ROLE.equals(role) || PICKUP_ROLE.equals(role) ? ITEM_AVAILABLE : ITEM_LOANED;
		final var rawItemStatus = itemStatus;
		final var requestStatus = HOLD_MISSING;
		final var rawRequestStatus = HOLD_MISSING;

		// Signal the item is available again
		// see; HandleSupplierItemAvailable transition
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
