package org.olf.dcb.core.model;

import java.util.List;
import java.util.UUID;

public class NoHomeIdentityException extends RuntimeException {
	public NoHomeIdentityException(UUID patronId,
		List<PatronIdentity> patronIdentities) {
		super("Patron \"%s\" has no home identity. Identities: %s"
			.formatted(patronId, patronIdentities));
	}
}
