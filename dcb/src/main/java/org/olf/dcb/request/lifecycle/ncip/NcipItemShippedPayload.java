package org.olf.dcb.request.lifecycle.ncip;

import java.time.Instant;

public record NcipItemShippedPayload(
	NcipParty party,
	String requestIdentifierValue,
	String itemAgencyId,
	String itemIdentifierValue,
	Instant dateShipped) {
}
