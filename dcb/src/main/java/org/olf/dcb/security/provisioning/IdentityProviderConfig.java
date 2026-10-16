package org.olf.dcb.security.provisioning;

import io.micronaut.core.annotation.NonNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * How to reach the identity provider that owns DCB Admin for Libraries accounts.
 *
 * <p>No {@code type} means no {@link IdentityProviderClient} bean and the mutations answer
 * "not configured on this deployment", which is what lets this ship everywhere before any
 * environment has a service account.
 *
 * <p>The variables, and the provider-side least privilege this assumes:
 * {@code operational:identity-provider-setup.adoc} §2.1–2.3 and §4.
 */
public interface IdentityProviderConfig {
	
	public static final String CONFIG_ROOT = "dcb.identity-provider";

	/** Base URL of the provider, with no trailing path. */
	@NotBlank
	@NotNull
	@NonNull
	String getBaseUrl();


	/** The confidential client whose service account performs provisioning. */
	@NotBlank
	@NotNull
	@NonNull
	String getClientId();

	/**
	 * No default: a deployment naming a provider without its secret must fail to start,
	 * rather than surface as a 401 weeks later in an environment somebody was told worked.
	 */
	@NotBlank
	@NotNull
	@NonNull
	String getClientSecret();
}
