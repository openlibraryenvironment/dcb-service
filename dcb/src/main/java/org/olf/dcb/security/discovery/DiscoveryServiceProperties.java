package org.olf.dcb.security.discovery;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.micronaut.context.annotation.ConfigurationProperties;

/**
 * Discovery services DCB will accept patron assertions from.
 *
 * A discovery service authenticates itself with a Keycloak client credential
 * carrying {@link org.olf.dcb.security.RoleNames#DISCOVERY_SERVICE}. That proves
 * WHO is calling. It does not prove WHICH PATRON they are calling for — so a
 * second, separately-keyed assertion carries the patron, and this configuration
 * is the trust anchor for it.
 *
 * Deliberately config-driven rather than a database table: the set of discovery
 * services is small, changes rarely, and onboarding one is a decision that should
 * leave an audit trail in ki-okapi-gitops rather than in a runtime admin screen.
 */
@ConfigurationProperties("dcb.discovery")
public class DiscoveryServiceProperties {

	private boolean enabled = false;
	private String audience = "dcb";
	private Duration maxAssertionLifetime = Duration.ofMinutes(5);
	private List<TrustedService> trustedServices = new ArrayList<>();

	/**
	 * When false (the default), DCB accepts no patron assertions at all and every
	 * /discovery/requests call fails closed. A deployment that has not been
	 * configured for discovery must not quietly behave as though it has.
	 */
	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	/** The audience a patron assertion must name. This DCB, and not some other one. */
	public String getAudience() {
		return audience;
	}

	public void setAudience(String audience) {
		this.audience = (audience != null && !audience.isBlank()) ? audience : "dcb";
	}

	/**
	 * Upper bound on assertion validity, enforced by DCB regardless of what the
	 * issuer put in `exp`. A discovery service that mints twelve-hour patron
	 * assertions has built a bearer token; this caps the replay window at
	 * something DCB chose.
	 */
	public Duration getMaxAssertionLifetime() {
		return maxAssertionLifetime;
	}

	public void setMaxAssertionLifetime(Duration maxAssertionLifetime) {
		this.maxAssertionLifetime = (maxAssertionLifetime != null && !maxAssertionLifetime.isZero())
			? maxAssertionLifetime
			: Duration.ofMinutes(5);
	}

	public List<TrustedService> getTrustedServices() {
		return trustedServices;
	}

	public void setTrustedServices(List<TrustedService> trustedServices) {
		this.trustedServices = trustedServices != null ? trustedServices : new ArrayList<>();
	}

	/**
	 * One onboarded discovery service. Mirrors
	 * {@code DcbPeerAuthProperties.TrustedPeerConfig} — same library underneath,
	 * different trust domain, so deliberately NOT the same config list. An NCIP
	 * peer must not become a patron-assertion issuer by accident.
	 */
	public static class TrustedService {
		private String serviceId;
		private String issuer;
		private URI jwksUri;
		/** Inline JWKS, for tests and air-gapped deployments. Takes precedence over jwksUri. */
		private Map<String, Object> jwks;

		public String getServiceId() {
			return serviceId;
		}

		public void setServiceId(String serviceId) {
			this.serviceId = serviceId;
		}

		public String getIssuer() {
			return issuer;
		}

		public void setIssuer(String issuer) {
			this.issuer = issuer;
		}

		public URI getJwksUri() {
			return jwksUri;
		}

		public void setJwksUri(URI jwksUri) {
			this.jwksUri = jwksUri;
		}

		public Map<String, Object> getJwks() {
			return jwks;
		}

		public void setJwks(Map<String, Object> jwks) {
			this.jwks = jwks;
		}
	}
}
