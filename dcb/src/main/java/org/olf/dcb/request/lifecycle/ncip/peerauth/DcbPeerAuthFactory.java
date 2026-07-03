package org.olf.dcb.request.lifecycle.ncip.peerauth;

import com.k_int.peerauth.service.DefaultPeerBindingValidator;
import com.k_int.peerauth.service.NimbusPeerTokenVerifier;
import com.k_int.peerauth.service.PeerBindingValidator;
import com.k_int.peerauth.service.PeerJwksService;
import com.k_int.peerauth.service.PeerTokenSigner;
import com.k_int.peerauth.service.PeerTokenVerifier;
import com.k_int.peerauth.service.TrustedPeerJwksResolver;
import com.k_int.peerauth.store.LocalPeerIdentityStore;
import com.k_int.peerauth.store.TrustedPeerStore;
import io.micronaut.context.annotation.Factory;
import jakarta.inject.Singleton;

@Factory
public class DcbPeerAuthFactory {
	@Singleton
	PeerTokenVerifier peerTokenVerifier(TrustedPeerStore trustedPeerStore) {
		return new NimbusPeerTokenVerifier(trustedPeerStore, new TrustedPeerJwksResolver());
	}

	@Singleton
	PeerBindingValidator peerBindingValidator(TrustedPeerStore trustedPeerStore) {
		return new DefaultPeerBindingValidator(trustedPeerStore);
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
