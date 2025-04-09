package org.olf.dcb.devtools.interaction.dummy.responder;

public interface LmsResponseStrategy {

	/**
	 * Determines if the given combination of state, role, and response type
	 * is supported by this strategy.
	 *
	 * @param state         the system state (e.g., "PICKUP_TRANSIT", "ITEM_RECEIVED")
	 * @param role          the role involved in the transaction (e.g., "supplier", "pickup")
	 * @param responseType  the type of response expected (e.g., "default", "custom")
	 * @return true if this strategy supports the given inputs, false otherwise
	 */
	boolean supports(String state, String role, String responseType);

	/**
	 * Generates the appropriate DummyResponse based on the provided role, state, and response type.
	 *
	 * @param state         the current system state
	 * @param role          the role involved in the transaction
	 * @param responseType  the type of response required
	 * @return a DummyResponse representing the system's response in this scenario
	 */
	DummyResponse getResponse(String state, String role, String responseType);
}
