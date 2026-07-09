package org.olf.dcb.request.lifecycle.ncip;

public record NcipLookupUserPayload(
	NcipParty party,
	String agencyId,
	String userIdentifierValue,
	String secret) {
	public boolean hasSecret() {
		return secret != null;
	}
}
