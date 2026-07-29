package org.olf.dcb.request.lifecycle.ncip.peerauth;

import com.k_int.peerauth.PeerAuthContext;
import com.k_int.peerauth.PeerAuthException;
import com.k_int.peerauth.PeerJwksDocument;
import com.k_int.peerauth.service.PeerJwksService;
import com.k_int.peerauth.store.LocalPeerIdentityStore;
import com.nimbusds.jose.jwk.JWK;
import java.util.List;

public class DcbPeerJwksService implements PeerJwksService {
	private final LocalPeerIdentityStore localPeerIdentityStore;

	public DcbPeerJwksService(LocalPeerIdentityStore localPeerIdentityStore) {
		this.localPeerIdentityStore = localPeerIdentityStore;
	}

	@Override
	public PeerJwksDocument publicJwks(PeerAuthContext context) {
		final var keys = localPeerIdentityStore.findSigningKeys(context).stream()
			.map(key -> publicJwk(key.publicJwk()))
			.toList();
		return new PeerJwksDocument(keys);
	}

	private static java.util.Map<String, Object> publicJwk(String jwkJson) {
		try {
			return JWK.parse(jwkJson).toJSONObject();
		}
		catch (Exception e) {
			throw new PeerAuthException("Could not parse public JWK", e);
		}
	}
}
