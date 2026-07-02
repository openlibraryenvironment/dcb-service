package org.olf.dcb.request.lifecycle.ncip;

public record NcipLookupItemSetPayload(
	NcipParty party,
	String bibliographicRecordIdentifier,
	String bibliographicRecordAgencyId) {

	public NcipLookupItemSetPayload {
		if (party == null) {
			throw new IllegalArgumentException("party is required");
		}
		requireText(bibliographicRecordIdentifier, "bibliographicRecordIdentifier");
		requireText(bibliographicRecordAgencyId, "bibliographicRecordAgencyId");
	}

	private static void requireText(String value, String name) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(name + " is required");
		}
	}
}
