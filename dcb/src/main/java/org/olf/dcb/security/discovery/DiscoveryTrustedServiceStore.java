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

import jakarta.inject.Singleton;
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
@Singleton
public class DiscoveryTrustedServiceStore implements TrustedPeerStore {

	private final Map<String, TrustedPeer> peersByIssuer;

	public DiscoveryTrustedServiceStore(DiscoveryServiceProperties properties) {
		this.peersByIssuer = properties.getTrustedServices().stream()
			.filter(DiscoveryTrustedServiceStore::isUsable)
			.collect(Collectors.toUnmodifiableMap(
				DiscoveryServiceProperties.TrustedService::getIssuer,
				service -> toTrustedPeer(service, properties.getAudience()),
				(first, duplicate) -> {
					// Two services claiming one issuer means one can mint the other's
					// assertions. Refuse to start rather than pick a winner.
					throw new IllegalStateException(
						"Duplicate discovery service issuer in dcb.discovery.trusted-services: "
							+ first.issuer());
				}));

		log.info("Discovery patron assertions accepted from {} trusted service(s): {}",
			peersByIssuer.size(), peersByIssuer.values().stream().map(TrustedPeer::peerId).toList());
	}

	private static boolean isUsable(DiscoveryServiceProperties.TrustedService service) {
		final var usable = service.getServiceId() != null && !service.getServiceId().isBlank()
			&& service.getIssuer() != null && !service.getIssuer().isBlank()
			&& (service.getJwksUri() != null || service.getJwks() != null);

		if (!usable) {
			log.error("Ignoring incomplete dcb.discovery.trusted-services entry (serviceId={}): "
				+ "serviceId, issuer and one of jwksUri/jwks are all required",
				service.getServiceId());
		}

		return usable;
	}

	private static TrustedPeer toTrustedPeer(DiscoveryServiceProperties.TrustedService service,
		String audience) {

		return new TrustedPeer(
			service.getServiceId(),
			service.getIssuer(),
			service.getJwksUri(),
			Set.of(audience),
			// The assertion's `sub` is the discovery service itself. See the class comment.
			Set.of(service.getServiceId()),
			PeerStatus.ACTIVE,
			Duration.ofMinutes(15),
			service.getJwks() != null ? Map.of("jwks", service.getJwks()) : Map.of());
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
