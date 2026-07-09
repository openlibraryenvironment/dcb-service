package org.olf.dcb.request.lifecycle.ncip;

public record NcipParty(
	String fromAgencyId,
	String toAgencyId,
	String fromSystemId,
	String toSystemId) {

	public NcipParty {
		requireText(fromAgencyId, "fromAgencyId");
		requireText(toAgencyId, "toAgencyId");
		requireText(fromSystemId, "fromSystemId");
		requireText(toSystemId, "toSystemId");
	}

	private static void requireText(String value, String name) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(name + " is required");
		}
	}
}
