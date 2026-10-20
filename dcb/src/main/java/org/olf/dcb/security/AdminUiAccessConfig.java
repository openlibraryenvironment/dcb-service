package org.olf.dcb.security;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.bind.annotation.Bindable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * The DCB Admin access bar, present only when its client id is set. With no client id there is no
 * bean, and {@link AdminUiAccessPolicy} permits every caller.
 */
@Context
@Requires(property = AdminUiAccessConfig.CLIENT_ID)
@ConfigurationProperties(AdminUiAccessConfig.ROOT)
public interface AdminUiAccessConfig {
	String ROOT = "dcb.security.admin-ui";
	String CLIENT_ID = ROOT + ".client-id";

	/** DCB Admin's OIDC client, matched against a token's {@code azp}. */
	@NotBlank
	@NonNull
	String getClientId();

	@NotNull
	@NonNull
	@Bindable(defaultValue = "WARN")
	AdminUiAccessPolicy.Mode getMode();
}
