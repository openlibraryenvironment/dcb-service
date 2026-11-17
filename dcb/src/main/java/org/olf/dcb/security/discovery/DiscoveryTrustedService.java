package org.olf.dcb.security.discovery;

import java.net.URI;
import java.util.Map;

import io.micronaut.core.annotation.Nullable;

/**
 * One onboarded discovery service. Mirrors {@code DcbPeerAuthProperties.TrustedPeerConfig}: the
 * same library underneath, a different trust domain, so deliberately not the same config list. An
 * NCIP peer must not become a patron-assertion issuer by accident.
 *
 * @param clientId the OIDC client whose bearer this service calls with, matched against {@code azp}
 * @param jwks an inline JWKS, for tests and deployments without egress. Takes precedence over jwksUri
 */
public record DiscoveryTrustedService(String serviceId, String issuer, @Nullable URI jwksUri,
	@Nullable String clientId, @Nullable Map<String, Object> jwks) {

	/** The client this service calls with: its service id unless it names another. */
	public String effectiveClientId() {
		return clientId != null ? clientId : serviceId;
	}
}
