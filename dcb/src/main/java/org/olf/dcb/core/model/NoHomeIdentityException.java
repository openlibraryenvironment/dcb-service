package org.olf.dcb.core.model;

import java.util.UUID;

public class NoHomeIdentityException extends RuntimeException {
	public NoHomeIdentityException(UUID patronId) {
		super("Patron \"" + patronId + "\" has no home identity");
	}
}
