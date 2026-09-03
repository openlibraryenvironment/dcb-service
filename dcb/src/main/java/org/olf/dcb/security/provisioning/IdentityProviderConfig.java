package org.olf.dcb.security.provisioning;

import java.util.Optional;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.bind.annotation.Bindable;
import jakarta.validation.constraints.NotBlank;

/**
 * How to reach the identity provider that owns DCB Admin for Libraries accounts.
 *
 * <p>No {@code type} means no {@link IdentityProviderClient} bean and the mutations answer
 * "not configured on this deployment", which is what lets this ship everywhere before any
 * environment has a service account.
 *
 * <p>The variables, and the provider-side least privilege this assumes:
 * {@code docs/identity-provider-setup.md} §2.1–2.3 and §4.
 */
@ConfigurationProperties("dcb.identity-provider")
public interface IdentityProviderConfig {

	/**
	 * {@code keycloak}, or another provider once implemented. Absent disables provisioning.
	 */
	Optional<String> getType();

	/** Base URL of the provider, with no trailing path. */
	Optional<String> getBaseUrl();

	/** Keycloak's realm. Ignored by providers that have no realm segment. */
	Optional<String> getRealm();

	/** The confidential client whose service account performs provisioning. */
	Optional<String> getClientId();

	/**
	 * No default: a deployment naming a provider without its secret must fail to start,
	 * rather than surface as a 401 weeks later in an environment somebody was told worked.
	 */
	@NonNull
	@NotBlank
	@Bindable(defaultValue = "")
	String getClientSecret();

	/**
	 * Zitadel needs the project whose roles are granted; Keycloak does not.
	 */
	Optional<String> getProjectId();
}
