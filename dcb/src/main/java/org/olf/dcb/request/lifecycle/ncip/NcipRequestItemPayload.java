package org.olf.dcb.request.lifecycle.ncip;

public record NcipRequestItemPayload(
	String userIdentifierValue,
	String bibliographicRecordIdentifier,
	String bibliographicRecordAgencyId,
	String requestIdentifierValue,
	String requestType,
	String requestScopeType) {

	public NcipRequestItemPayload {
		requireText(userIdentifierValue, "userIdentifierValue");
		requireText(bibliographicRecordIdentifier, "bibliographicRecordIdentifier");
		requireText(bibliographicRecordAgencyId, "bibliographicRecordAgencyId");
		requireText(requestIdentifierValue, "requestIdentifierValue");
		requireText(requestType, "requestType");
		requireText(requestScopeType, "requestScopeType");
	}

	private static void requireText(String value, String name) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(name + " is required");
		}
	}
}
