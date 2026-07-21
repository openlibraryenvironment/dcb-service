package org.olf.dcb.request.lifecycle.ncip.peerauth;

import com.k_int.peerauth.LocalPeerIdentity;
import com.k_int.peerauth.LocalSigningKey;
import com.k_int.peerauth.PeerAuthContext;
import com.k_int.peerauth.PeerStatus;
import com.k_int.peerauth.SigningKeyStatus;
import com.k_int.peerauth.TrustedPeer;
import com.k_int.peerauth.TrustedPeerBinding;
import com.k_int.peerauth.store.LocalPeerIdentityStore;
import com.k_int.peerauth.store.TrustedPeerStore;
import jakarta.inject.Singleton;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Singleton
public class DcbPeerAuthStore implements LocalPeerIdentityStore, TrustedPeerStore {
	private final DcbPeerAuthProperties properties;

	public DcbPeerAuthStore(DcbPeerAuthProperties properties) {
		this.properties = properties;
	}

	@Override
	public Optional<LocalPeerIdentity> findLocalIdentity(PeerAuthContext context) {
		final var identity = properties.getLocalIdentity();
		if (isBlank(identity.getIssuer()) || isBlank(identity.getSubject())
			|| isBlank(identity.getKeyId()) || isBlank(identity.getPublicJwk())) {
			return Optional.empty();
		}

		return Optional.of(new LocalPeerIdentity(
			identity.getId(),
			identity.getIssuer(),
			identity.getSubject(),
			identity.getAudiences(),
			identity.getJwksUri(),
			identity.getKeyId(),
			Map.of()));
	}

	@Override
	public List<LocalSigningKey> findSigningKeys(PeerAuthContext context) {
		final var identity = properties.getLocalIdentity();
		if (isBlank(identity.getKeyId()) || isBlank(identity.getPublicJwk())
			|| isBlank(identity.getPrivateJwk())) {
			return List.of();
		}

		return List.of(new LocalSigningKey(
			identity.getKeyId(),
			"RS256",
			identity.getPublicJwk(),
			identity.getKeyId(),
			null,
			null,
			SigningKeyStatus.ACTIVE,
			Map.of("privateJwk", identity.getPrivateJwk())));
	}

	@Override
	public Optional<TrustedPeer> findByIssuer(PeerAuthContext context, String issuer) {
		return properties.getTrustedPeers().stream()
			.filter(peer -> issuer != null && issuer.equals(peer.getIssuer()))
			.findFirst()
			.map(peer -> new TrustedPeer(
				peer.getPeerId(),
				peer.getIssuer(),
				peer.getJwksUri(),
				peer.getAudiences(),
				peer.getSubjects(),
				PeerStatus.valueOf(peer.getStatus()),
				null,
				peer.getJwks() != null ? Map.of("jwks", peer.getJwks()) : Map.of()));
	}

	@Override
	public List<TrustedPeerBinding> findBindings(PeerAuthContext context, String peerId) {
		return properties.getTrustedPeers().stream()
			.filter(peer -> peerId != null && peerId.equals(peer.getPeerId()))
			.flatMap(peer -> peer.getBindings().stream()
				.map(binding -> new TrustedPeerBinding(
					peerId,
					binding.getProtocol(),
					binding.getSystemId(),
					null,
					Map.of())))
			.toList();
	}

	private static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}
}
