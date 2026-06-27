package org.olf.dcb.request.lifecycle.ncip;

public record NcipAcceptItemPayload(
	String requestIdentifierValue,
	String requestedActionType,
	String userIdentifierValue,
	String itemIdentifierValue) {

	public NcipAcceptItemPayload {
		requireText(requestIdentifierValue, "requestIdentifierValue");
		requireText(requestedActionType, "requestedActionType");
	}

	private static void requireText(String value, String name) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(name + " is required");
		}
	}
}
