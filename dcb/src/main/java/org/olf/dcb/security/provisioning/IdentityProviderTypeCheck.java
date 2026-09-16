package org.olf.dcb.security.provisioning;

import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.Value;

/**
 * A provider type nobody implements is a typo, not a decision. Eager, so it fails the
 * deploy rather than the first account somebody tries to create.
 *
 * <p>Takes the property, not {@link IdentityProviderConfig}: injecting that validates it,
 * and its {@code @NotBlank} secret then makes every deployment with provisioning switched
 * off fail to start. Empty turns provisioning off, and stays silent.
 */
@Context
public class IdentityProviderTypeCheck {

	private static final String SUPPORTED = "keycloak";

	public IdentityProviderTypeCheck(@Value("${dcb.identity-provider.type:}") String type) {
		if (type == null || type.isBlank() || SUPPORTED.equals(type)) {
			return;
		}

		throw new IllegalStateException("dcb.identity-provider.type \"" + type
			+ "\" is not supported. Use \"" + SUPPORTED + "\", or leave it empty to turn "
			+ "account provisioning off. See operational:identity-provider-setup.adoc");
	}
}
