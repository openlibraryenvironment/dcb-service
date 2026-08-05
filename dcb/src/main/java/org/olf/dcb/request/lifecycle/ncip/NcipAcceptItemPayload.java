package org.olf.dcb.request.lifecycle.ncip;

public record NcipAcceptItemPayload(
	NcipParty party,
	String requestIdentifierValue,
	String requestedActionType,
	String userAgencyId,
	String userIdentifierValue,
	String itemAgencyId,
	String itemIdentifierType,
	String itemIdentifierValue,
	NcipBibliographicDescription bibliographicDescription) {

	public NcipAcceptItemPayload {
		if (party == null) {
			throw new IllegalArgumentException("party is required");
		}
		requireText(requestIdentifierValue, "requestIdentifierValue");
		requireText(requestedActionType, "requestedActionType");
	}

	private static void requireText(String value, String name) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(name + " is required");
		}
	}
}
