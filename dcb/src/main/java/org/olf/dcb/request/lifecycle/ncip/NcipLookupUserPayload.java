package org.olf.dcb.request.lifecycle.ncip;

public record NcipLookupUserPayload(
	String agencyId,
	String userIdentifierValue,
	String secret) {
	public boolean hasSecret() {
		return secret != null;
	}
}
