package org.olf.dcb.request.fulfilment;

import java.util.UUID;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

/**
 * What happened to one virtual patron. {@code outcome} is DELETED, EXPIRED, ALREADY_GONE (the
 * library's system no longer had it, so only DCB's record was removed), or, when every virtual
 * patron of a borrower is removed, the code of the refusal or FAILED.
 */
@Serdeable
public record VirtualPatronRemovalResult(UUID identityId, @Nullable String hostLmsCode, String outcome,
	@Nullable String message) {

	static VirtualPatronRemovalResult of(UUID identityId, String hostLmsCode, String outcome) {
		return new VirtualPatronRemovalResult(identityId, hostLmsCode, outcome, null);
	}
}
