package org.olf.dcb.security.provisioning;

import io.micronaut.serde.annotation.Serdeable;

/**
 * Where an account is in its life. The identity provider owns this fact, so what is stored
 * is the last state DCB saw — rendered when the provider is unreachable, and reconciled
 * against a live read on every listing.
 */
@Serdeable
public enum LibraryUserStatus {
	/** Created and sent an actions email; has not yet set a password. */
	INVITED,
	/** Enabled at the provider and has completed the actions email. */
	ACTIVE,
	/** Explicitly disabled. The account still exists; it cannot sign in. */
	DISABLED
}
