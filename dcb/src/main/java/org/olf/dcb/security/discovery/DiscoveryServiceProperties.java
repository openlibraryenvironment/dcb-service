package org.olf.dcb.security.discovery;

import java.net.URI;
import java.text.ParseException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.nimbusds.jose.util.JSONArrayUtils;

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
 * leave an audit trail in whatever repository holds the deployment's configuration,
 * rather than in a runtime admin screen.
 *
 * TWO WAYS IN, ONE EFFECTIVE LIST. {@code trusted-services} is the YAML form, for a
 * deployment that can mount a config file. {@code trusted-services-json} is the same
 * data as a JSON string in ONE value, for a deployment that only has environment
 * variables — see {@link #setTrustedServicesJson}. Both feed
 * {@link #getTrustedServices()}, so everything downstream sees one list and the
 * duplicate-issuer check in {@link DiscoveryTrustedServiceStore} covers both sources.
 */
@ConfigurationProperties("dcb.discovery")
public class DiscoveryServiceProperties {

	private boolean enabled = false;
	private String audience = "dcb";
	private Duration maxAssertionLifetime = Duration.ofMinutes(5);
	private List<TrustedService> trustedServices = new ArrayList<>();
	private List<TrustedService> trustedServicesFromJson = new ArrayList<>();

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

	/**
	 * The effective trust anchor: the YAML list and the JSON list concatenated.
	 *
	 * Merged HERE rather than in either setter because Micronaut does not guarantee
	 * the order it calls them in, and a merge in {@code setTrustedServices} would be
	 * silently undone depending on which arrived second. Concatenating on read is
	 * order-independent and has no state to get wrong.
	 */
	public List<TrustedService> getTrustedServices() {
		if (trustedServicesFromJson.isEmpty()) {
			return trustedServices;
		}

		final var effective = new ArrayList<>(trustedServices);
		effective.addAll(trustedServicesFromJson);

		return effective;
	}

	public void setTrustedServices(List<TrustedService> trustedServices) {
		this.trustedServices = trustedServices != null ? trustedServices : new ArrayList<>();
	}

	/**
	 * The whole trusted-services list as a JSON array in a SINGLE value, so that a
	 * deployment with nothing but environment variables can still onboard a discovery
	 * service:
	 *
	 * <pre>
	 * DCB_DISCOVERY_TRUSTED_SERVICES_JSON=[{"service-id":"x","issuer":"https://x.example.org",
	 *   "jwks-uri":"https://x.example.org/jwks.json"}]
	 * </pre>
	 *
	 * WHY THIS EXISTS AT ALL. Micronaut maps an environment variable onto every
	 * dot/hyphen permutation of its name, but an index stays a dot segment —
	 * {@code DCB_DISCOVERY_TRUSTED_SERVICES_0_SERVICE_ID} becomes
	 * {@code dcb.discovery.trusted-services.0.service-id}, never the
	 * {@code trusted-services[0].service-id} form that list binding needs. So a list of
	 * objects cannot be expressed as environment variables at all, and a deployment
	 * without a mountable config file would otherwise have no way in. Pinned by
	 * DiscoveryServicePropertiesBindingTests.
	 *
	 * Keys are kebab-case, matching the YAML exactly — one spelling, so a config
	 * reviewed in one form reads the same in the other.
	 *
	 * Parsed eagerly and FATALLY: malformed JSON, or an entry that is not an object,
	 * fails the application at startup. A trust anchor that silently binds nothing
	 * fails closed and looks exactly like a deployment nobody configured, which is the
	 * one outcome worth crashing to avoid.
	 */
	public void setTrustedServicesJson(String trustedServicesJson) {
		this.trustedServicesFromJson = parseTrustedServices(trustedServicesJson);
	}

	private static List<TrustedService> parseTrustedServices(String json) {
		if (json == null || json.isBlank()) {
			return new ArrayList<>();
		}

		final List<Object> entries;

		try {
			// Nimbus is already a dependency for JWT verification, and its parser
			// returns plain maps. No new JSON stack for one config value.
			entries = JSONArrayUtils.parse(json);
		} catch (ParseException e) {
			throw new IllegalArgumentException(
				"dcb.discovery.trusted-services-json is not a JSON array: " + e.getMessage(), e);
		}

		final var parsed = new ArrayList<TrustedService>();

		for (final var entry : entries) {
			if (!(entry instanceof Map<?, ?> fields)) {
				throw new IllegalArgumentException(
					"dcb.discovery.trusted-services-json entries must be JSON objects, found: " + entry);
			}

			final var service = new TrustedService();

			service.setServiceId(text(fields, "service-id"));
			service.setIssuer(text(fields, "issuer"));

			final var jwksUri = text(fields, "jwks-uri");

			if (jwksUri != null) {
				try {
					service.setJwksUri(URI.create(jwksUri));
				} catch (IllegalArgumentException e) {
					throw new IllegalArgumentException(
						"dcb.discovery.trusted-services-json has an unusable jwks-uri: " + jwksUri, e);
				}
			}

			if (fields.get("jwks") instanceof Map<?, ?> jwks) {
				@SuppressWarnings("unchecked")
				final var inlineJwks = (Map<String, Object>) jwks;
				service.setJwks(inlineJwks);
			}

			parsed.add(service);
		}

		return parsed;
	}

	private static String text(Map<?, ?> fields, String key) {
		return fields.get(key) instanceof String value && !value.isBlank() ? value : null;
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
