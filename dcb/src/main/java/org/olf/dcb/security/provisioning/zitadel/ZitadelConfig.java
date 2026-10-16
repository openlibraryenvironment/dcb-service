package org.olf.dcb.security.provisioning.zitadel;

import org.olf.dcb.security.provisioning.IdentityProviderConfig;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.annotation.NonNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@ConfigurationProperties("dcb.identity-provider.zitadel")
public interface ZitadelConfig extends IdentityProviderConfig {

	/**
	 * Zitadel needs the project whose roles are granted
	 */
	@NotBlank
	@NotNull
	@NonNull
	String getProjectId();
}
