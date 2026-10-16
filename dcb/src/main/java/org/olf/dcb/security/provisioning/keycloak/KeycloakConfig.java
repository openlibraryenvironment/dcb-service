package org.olf.dcb.security.provisioning.keycloak;

import org.olf.dcb.security.provisioning.IdentityProviderConfig;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.NonNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Context
@Requires(property = IdentityProviderConfig.CONFIG_ROOT + ".keycloak")
@ConfigurationProperties(IdentityProviderConfig.CONFIG_ROOT + ".keycloak")
public interface KeycloakConfig extends IdentityProviderConfig {

	/** Keycloak's realm. */
	@NotBlank
	@NotNull
	@NonNull
	String getRealm();
}
