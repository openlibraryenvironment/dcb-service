package org.olf.dcb.request.lifecycle.ncip.peerauth;

import com.k_int.peerauth.service.PeerJwksResolver;
import com.k_int.peerauth.service.PeerJwksService;
import com.k_int.peerauth.service.PeerTokenSigner;
import com.k_int.peerauth.service.TrustedPeerJwksResolver;
import com.k_int.peerauth.store.LocalPeerIdentityStore;
import io.micronaut.context.annotation.Factory;
import jakarta.inject.Singleton;

@Factory
public class DcbPeerAuthFactory {
	@Singleton
	PeerJwksResolver peerJwksResolver(DcbPeerAuthProperties properties) {
		final var remoteJwks = properties.getRemoteJwks();
		// Trusted peer endpoints can still stall; keep their network wait deployment-bounded.
		return new TrustedPeerJwksResolver(
			remoteJwks.getConnectTimeout(), remoteJwks.getReadTimeout());
	}

	@Singleton
	PeerTokenSigner peerTokenSigner(LocalPeerIdentityStore localPeerIdentityStore) {
		return new NimbusPeerTokenSigner(localPeerIdentityStore);
	}

	@Singleton
	PeerJwksService peerJwksService(LocalPeerIdentityStore localPeerIdentityStore) {
		return new DcbPeerJwksService(localPeerIdentityStore);
	}
}
