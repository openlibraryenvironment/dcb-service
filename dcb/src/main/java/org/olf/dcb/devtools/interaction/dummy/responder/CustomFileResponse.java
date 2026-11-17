package org.olf.dcb.devtools.interaction.dummy.responder;

import static org.olf.dcb.devtools.interaction.dummy.responder.DummyConstants.CUSTOM_RESPONSE_TYPE;

import java.util.Objects;
import java.util.Set;

import jakarta.inject.Singleton;

/**
 * Any state the workflow responders do not know, answered from dummy-responses.json: a
 * cancelled hold, a missing one, a renewed loan, a hold on a lent item. The known states
 * keep their responders, which read the same file when the response type is custom.
 */
@Singleton
public class CustomFileResponse implements LmsResponseStrategy {
	private static final Set<String> STATES_WITH_A_RESPONDER = Set.of(
		"PICKUP_TRANSIT", "RECEIVED_AT_PICKUP", "READY_FOR_PICKUP", "LOANED", "RETURN_TRANSIT", "COMPLETED");

	@Override
	public boolean supports(String state, String role, String responseType) {
		return Objects.equals(responseType, CUSTOM_RESPONSE_TYPE)
			&& state != null
			&& !STATES_WITH_A_RESPONDER.contains(state.toUpperCase())
			&& FileResponseProvider.hasResponse(state, role);
	}

	@Override
	public DummyResponse getResponse(String state, String role, String responseType) {
		return FileResponseProvider.getResponse(state, role);
	}
}
