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

	/** Documented as PT2M in README.md and docs/discovery-service-approach.md. */
	private static final Duration DEFAULT_MAX_ASSERTION_LIFETIME = Duration.ofMinutes(2);

	private boolean enabled = false;
	private String audience = "dcb";
	private Duration maxAssertionLifetime = DEFAULT_MAX_ASSERTION_LIFETIME;
	private int maxLoans = 25;
	private int loanLookupConcurrency = 2;
	private Duration loanLookupTimeout = Duration.ofSeconds(8);
	private int maxAccountRows = 25;
	private Duration accountLookupTimeout = Duration.ofSeconds(8);
	private Duration patronResolveTimeout = Duration.ofSeconds(8);
	private int patronResolvesPerMinute = 600;
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

	/**
	 * How many loans one patron may be told about, and therefore how many live item reads
	 * a single account page may cost the borrowing library. DCB records no due date, so
	 * each loan is one read; this is the bound on that fan-out.
	 */
	public int getMaxLoans() {
		return maxLoans;
	}

	public void setMaxLoans(int maxLoans) {
		this.maxLoans = maxLoans > 0 ? maxLoans : 25;
	}

	/**
	 * How many of those reads may be in flight at once. Low deliberately: every one of
	 * them goes to the SAME member system, because the borrowing library is the patron's
	 * own, so this is concurrency against one library rather than across many.
	 */
	public int getLoanLookupConcurrency() {
		return loanLookupConcurrency;
	}

	public void setLoanLookupConcurrency(int loanLookupConcurrency) {
		this.loanLookupConcurrency = loanLookupConcurrency > 0 ? loanLookupConcurrency : 2;
	}

	/**
	 * How long one of those reads may take before the loan is reported with no due date.
	 * A slow member system must degrade the page, never hold it open.
	 */
	public Duration getLoanLookupTimeout() {
		return loanLookupTimeout;
	}

	public void setLoanLookupTimeout(Duration loanLookupTimeout) {
		this.loanLookupTimeout = (loanLookupTimeout != null && !loanLookupTimeout.isZero())
			? loanLookupTimeout
			: Duration.ofSeconds(8);
	}

	/**
	 * How many loans and how many charges one call may return from a patron's own library.
	 * Unlike the loans list this is ONE read rather than one per row, so the bound is
	 * about what a page should render rather than about a fan-out.
	 */
	public int getMaxAccountRows() {
		return maxAccountRows;
	}

	public void setMaxAccountRows(int maxAccountRows) {
		this.maxAccountRows = maxAccountRows > 0 ? maxAccountRows : 25;
	}

	/**
	 * How long that read may take before the account is reported as unavailable. A slow
	 * member system must degrade the answer, never hold the page open.
	 */
	public Duration getAccountLookupTimeout() {
		return accountLookupTimeout;
	}

	public void setAccountLookupTimeout(Duration accountLookupTimeout) {
		this.accountLookupTimeout = (accountLookupTimeout != null && !accountLookupTimeout.isZero())
			? accountLookupTimeout
			: Duration.ofSeconds(8);
	}

	/** How long looking an institution sign-in up at its library may take before it is refused. */
	public Duration getPatronResolveTimeout() {
		return patronResolveTimeout;
	}

	public void setPatronResolveTimeout(Duration patronResolveTimeout) {
		this.patronResolveTimeout = (patronResolveTimeout != null && !patronResolveTimeout.isZero())
			? patronResolveTimeout
			: Duration.ofSeconds(8);
	}

	/**
	 * How many institution sign-ins one trusted service may resolve in a minute. Each is a live
	 * read at a member library, and the route can name any library in the consortium.
	 */
	public int getPatronResolvesPerMinute() {
		return patronResolvesPerMinute;
	}

	public void setPatronResolvesPerMinute(int patronResolvesPerMinute) {
		this.patronResolvesPerMinute = patronResolvesPerMinute > 0 ? patronResolvesPerMinute : 600;
	}

	/** The audience a patron assertion must name. This DCB, and not some other one. */
	public String getAudience() {
		return audience;
	}

	public void setAudience(String audience) {
		if (audience == null || audience.isBlank()) {
			throw new IllegalArgumentException("dcb.discovery.audience is set but blank. Remove it to use \"dcb\", "
				+ "or name this DCB");
		}

		this.audience = audience;
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
		if (maxAssertionLifetime == null || maxAssertionLifetime.isZero() || maxAssertionLifetime.isNegative()) {
			throw new IllegalArgumentException("dcb.discovery.max-assertion-lifetime must be positive, or unset "
				+ "for " + DEFAULT_MAX_ASSERTION_LIFETIME + ": " + maxAssertionLifetime);
		}

		this.maxAssertionLifetime = maxAssertionLifetime;
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
		if (json == null) {
			return new ArrayList<>();
		}

		if (json.isBlank()) {
			throw new IllegalArgumentException("dcb.discovery.trusted-services-json is set but blank. Remove it, "
				+ "or give a JSON array of trusted services");
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

			service.setClientId(text(fields, "client-id"));

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
		final var value = fields.get(key);

		if (value == null) {
			return null;
		}

		if (!(value instanceof String text) || text.isBlank()) {
			throw new IllegalArgumentException(
				"dcb.discovery.trusted-services-json: \"" + key + "\" must be a non-blank string, or left out");
		}

		return text;
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
		/** The OIDC client whose bearer this service calls with, matched against {@code azp}. */
		private String clientId;
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

		/** Defaults to the service id, which is the client id when the service signs as its client. */
		public String getClientId() {
			return clientId != null ? clientId : serviceId;
		}

		public void setClientId(String clientId) {
			if (clientId != null && clientId.isBlank()) {
				throw new IllegalArgumentException("A dcb.discovery trusted service's client-id is set but blank. "
					+ "Remove it to use the service-id, or set the service's OIDC client id");
			}

			this.clientId = clientId;
		}

		public Map<String, Object> getJwks() {
			return jwks;
		}

		public void setJwks(Map<String, Object> jwks) {
			this.jwks = jwks;
		}
	}
}
