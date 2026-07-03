package org.olf.dcb.request.lifecycle.ncip.peerauth;

import com.k_int.peerauth.PeerAuthContext;
import com.k_int.peerauth.PeerTokenRequest;
import com.k_int.peerauth.service.PeerTokenSigner;
import io.micronaut.http.MutableHttpRequest;
import jakarta.inject.Singleton;
import java.util.Map;
import org.olf.dcb.core.model.HostLms;
import org.olf.dcb.request.lifecycle.ncip.NcipIdentityConfiguration;

@Singleton
public class NcipPeerAuthorizationService {
	private final DcbPeerAuthProperties properties;
	private final PeerTokenSigner peerTokenSigner;
	private final NcipIdentityConfiguration ncipIdentityConfiguration;

	public NcipPeerAuthorizationService(
		DcbPeerAuthProperties properties,
		PeerTokenSigner peerTokenSigner,
		NcipIdentityConfiguration ncipIdentityConfiguration) {

		this.properties = properties;
		this.peerTokenSigner = peerTokenSigner;
		this.ncipIdentityConfiguration = ncipIdentityConfiguration;
	}

	public <T> MutableHttpRequest<T> authorize(MutableHttpRequest<T> request, HostLms hostLms) {
		if (!properties.isNcipEnabled()) {
			return request;
		}

		final var identity = properties.getLocalIdentity();
		final var token = peerTokenSigner.sign(
			PeerAuthContext.singleTenant(NcipPeerAuth.LOCAL_IDENTITY),
			new PeerTokenRequest(
				identity.getAudiences(),
				identity.getSubject(),
				identity.getTokenLifetime(),
				Map.of(
					"protocol", NcipPeerAuth.PROTOCOL,
					"systemId", ncipIdentityConfiguration.getSystemId(),
					"hostLmsCode", hostLms.getCode())));

		return request.bearerAuth(token);
	}
}
