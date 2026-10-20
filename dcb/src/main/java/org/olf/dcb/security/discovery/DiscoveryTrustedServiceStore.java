package org.olf.dcb.security.discovery;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.k_int.peerauth.PeerAuthContext;
import com.k_int.peerauth.PeerStatus;
import com.k_int.peerauth.TrustedPeer;
import com.k_int.peerauth.TrustedPeerBinding;
import com.k_int.peerauth.store.TrustedPeerStore;

import org.olf.dcb.security.AdminUiAccessPolicy;

import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.Environment;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.security.authentication.Authentication;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

/**
 * The onboarded discovery services, indexed by JWT issuer.
 *
 * Built ONCE from configuration into an immutable map. It is bounded by the config
 * file, not by traffic, so it is not the unbounded-map anti-pattern the backend
 * doctrine bans — but do not be tempted to make it a cache keyed on anything a
 * caller controls.
 *
 * The peer's accepted `subjects` set is the service id, NOT the patron. Patron ids
 * are unbounded and caller-supplied; an allow-list of them is neither possible nor
 * desirable. The patron travels in claims, verified separately by
 * {@link PatronAssertionVerifier}.
 */
@Slf4j
// Created at startup, not on first use: a duplicate issuer or malformed trust JSON must stop the
// application starting, not fail the first patron call after a green rollout
@Context
@Requires(property = DiscoveryConfig.ENABLED, value = "true")
public class DiscoveryTrustedServiceStore implements TrustedPeerStore {
	static final String YAML_LIST = DiscoveryConfig.ROOT + ".trusted-services";

	private final Map<String, TrustedPeer> peersByIssuer;
	private final Map<String, DiscoveryTrustedService> servicesById;
	private final Map<String, DiscoveryTrustedService> servicesByClientId;

	@Inject
	public DiscoveryTrustedServiceStore(DiscoveryConfig config, Environment environment) {
		this(config, environment.containsProperty(YAML_LIST));
	}

	DiscoveryTrustedServiceStore(DiscoveryConfig config, boolean yamlListPresent) {
		// A YAML list was documented once and bound nothing; refuse it rather than ignore it again
		if (yamlListPresent) {
			throw new IllegalStateException(YAML_LIST + " is not read. Give the trusted services as one JSON "
				+ "array in " + DiscoveryTrustedServices.PROPERTY + " (DCB_DISCOVERY_TRUSTED_SERVICES_JSON)");
		}

		requirePositive("max-assertion-lifetime", config.getMaxAssertionLifetime());
		requirePositive("loan-lookup-timeout", config.getLoanLookupTimeout());
		requirePositive("account-lookup-timeout", config.getAccountLookupTimeout());
		requirePositive("patron-resolve-timeout", config.getPatronResolveTimeout());

		final var usable = DiscoveryTrustedServices.parse(config.getTrustedServicesJson());

		usable.forEach(DiscoveryTrustedServiceStore::requireComplete);

		this.servicesById = usable.stream().collect(Collectors.toUnmodifiableMap(
			DiscoveryTrustedService::serviceId, service -> service,
			(first, duplicate) -> {
				throw new IllegalStateException(
					"Duplicate discovery service id in " + DiscoveryTrustedServices.PROPERTY + ": "
						+ first.serviceId());
			}));

		// One client acting as two services could spend assertions either one signed.
		this.servicesByClientId = usable.stream().collect(Collectors.toUnmodifiableMap(
			DiscoveryTrustedService::effectiveClientId, service -> service,
			(first, duplicate) -> {
				throw new IllegalStateException(
					"Duplicate discovery service client-id in " + DiscoveryTrustedServices.PROPERTY + ": "
						+ first.effectiveClientId());
			}));

		this.peersByIssuer = usable.stream()
			.collect(Collectors.toUnmodifiableMap(
				DiscoveryTrustedService::issuer,
				service -> toTrustedPeer(service, config.getAudience()),
				(first, duplicate) -> {
					// Two services claiming one issuer means one can mint the other's
					// assertions. Refuse to start rather than pick a winner.
					throw new IllegalStateException(
						"Duplicate discovery service issuer in " + DiscoveryTrustedServices.PROPERTY + ": "
							+ first.issuer());
				}));

		log.info("Discovery patron assertions accepted from {} trusted service(s): {}",
			peersByIssuer.size(), peersByIssuer.values().stream().map(TrustedPeer::peerId).toList());
	}

	private static void requirePositive(String setting, Duration value) {
		if (value.isZero() || value.isNegative()) {
			throw new IllegalStateException(DiscoveryConfig.ROOT + "." + setting + " must be positive: " + value);
		}
	}

	private static void requireComplete(DiscoveryTrustedService service) {
		final var complete = service.serviceId() != null && service.issuer() != null
			&& (service.jwksUri() != null || service.jwks() != null);

		if (!complete) {
			throw new IllegalStateException("Incomplete " + DiscoveryTrustedServices.PROPERTY + " entry (service-id="
				+ service.serviceId() + "): service-id, issuer and one of jwks-uri/jwks are all required");
		}
	}

	public Optional<DiscoveryTrustedService> findByServiceId(String serviceId) {
		return Optional.ofNullable(serviceId).map(servicesById::get);
	}

	/** The service whose OIDC client obtained the caller's bearer, if any. */
	public Optional<DiscoveryTrustedService> findByCaller(@Nullable Authentication caller) {
		return Optional.ofNullable(caller)
			.map(authentication -> authentication.getAttributes().get(AdminUiAccessPolicy.AUTHORIZED_PARTY))
			.filter(String.class::isInstance)
			.map(String.class::cast)
			.map(servicesByClientId::get);
	}

	private static TrustedPeer toTrustedPeer(DiscoveryTrustedService service, String audience) {
		return new TrustedPeer(
			service.serviceId(),
			service.issuer(),
			service.jwksUri(),
			Set.of(audience),
			// The assertion's `sub` is the discovery service itself. See the class comment.
			Set.of(service.serviceId()),
			PeerStatus.ACTIVE,
			Duration.ofMinutes(15),
			service.jwks() != null ? Map.of("jwks", service.jwks()) : Map.of());
	}

	@Override
	public Optional<TrustedPeer> findByIssuer(PeerAuthContext context, String issuer) {
		return Optional.ofNullable(peersByIssuer.get(issuer));
	}

	@Override
	public List<TrustedPeerBinding> findBindings(PeerAuthContext context, String peerId) {
		// Discovery services bind to no protocol-specific identity. The patron
		// binding is done in PatronAssertionVerifier, against claims.
		return List.of();
	}
}
