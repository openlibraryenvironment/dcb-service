package org.olf.dcb.request.lifecycle.ncip;

public record NcipRequestItemPayload(
	NcipParty party,
	String userAgencyId,
	String userIdentifierValue,
	String bibliographicRecordIdentifier,
	String bibliographicRecordAgencyId,
	String itemAgencyId,
	String itemIdentifierType,
	String itemIdentifierValue,
	String localItemIdentifierValue,
	String requestIdentifierValue,
	String requestType,
	String requestScopeType,
	NcipBibliographicDescription bibliographicDescription) {

	public NcipRequestItemPayload(
		NcipParty party,
		String userAgencyId,
		String userIdentifierValue,
		String bibliographicRecordIdentifier,
		String bibliographicRecordAgencyId,
		String itemAgencyId,
		String itemIdentifierType,
		String itemIdentifierValue,
		String requestIdentifierValue,
		String requestType,
		String requestScopeType
	) {
		this(party, userAgencyId, userIdentifierValue, bibliographicRecordIdentifier,
			bibliographicRecordAgencyId, itemAgencyId, itemIdentifierType, itemIdentifierValue,
			null, requestIdentifierValue, requestType, requestScopeType, null);
	}

	public NcipRequestItemPayload {
		if (party == null) {
			throw new IllegalArgumentException("party is required");
		}
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
