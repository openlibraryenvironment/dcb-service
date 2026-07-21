package org.olf.dcb.request.lifecycle.ncip.peerauth;

import com.k_int.peerauth.PeerAuthContext;
import com.k_int.peerauth.PeerAuthException;
import com.k_int.peerauth.PeerTokenRequest;
import com.k_int.peerauth.SigningKeyStatus;
import com.k_int.peerauth.service.PeerTokenSigner;
import com.k_int.peerauth.store.LocalPeerIdentityStore;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import java.util.Map;

public class NimbusPeerTokenSigner implements PeerTokenSigner {
	private final LocalPeerIdentityStore localPeerIdentityStore;
	private final Clock clock;

	public NimbusPeerTokenSigner(LocalPeerIdentityStore localPeerIdentityStore) {
		this(localPeerIdentityStore, Clock.systemUTC());
	}

	NimbusPeerTokenSigner(LocalPeerIdentityStore localPeerIdentityStore, Clock clock) {
		this.localPeerIdentityStore = localPeerIdentityStore;
		this.clock = clock;
	}

	@Override
	public String sign(PeerAuthContext context, PeerTokenRequest request) {
		try {
			final var identity = localPeerIdentityStore.findLocalIdentity(context)
				.orElseThrow(() -> new PeerAuthException("Local peer identity is not configured"));
			final var signingKey = localPeerIdentityStore.findSigningKeys(context).stream()
				.filter(key -> key.status() == SigningKeyStatus.ACTIVE)
				.filter(key -> identity.activeKeyId().equals(key.keyId()))
				.findFirst()
				.orElseThrow(() -> new PeerAuthException("Active local signing key is not configured"));
			final var privateJwk = signingKey.attributes().get("privateJwk");
			if (!(privateJwk instanceof String jwkJson) || jwkJson.isBlank()) {
				throw new PeerAuthException("Active local signing key has no private JWK");
			}

			final var rsaKey = toRsaKey(jwkJson);
			final var now = clock.instant();
			final var lifetime = request.lifetime() != null
				? request.lifetime()
				: Duration.ofMinutes(5);
			final var audiences = request.audiences().isEmpty()
				? identity.audiences()
				: request.audiences();
			final var claims = new JWTClaimsSet.Builder()
				.issuer(identity.issuer())
				.subject(firstText(request.subject(), identity.subject()))
				.audience(new ArrayList<>(audiences))
				.issueTime(Date.from(now))
				.notBeforeTime(Date.from(now))
				.expirationTime(Date.from(now.plus(lifetime)));
			request.claims().forEach(claims::claim);

			final var jwt = new SignedJWT(
				new JWSHeader.Builder(JWSAlgorithm.RS256)
					.keyID(signingKey.keyId())
					.type(JOSEObjectType.JWT)
					.build(),
				claims.build());
			jwt.sign(new RSASSASigner(rsaKey));
			return jwt.serialize();
		}
		catch (PeerAuthException e) {
			throw e;
		}
		catch (Exception e) {
			throw new PeerAuthException("Could not sign peer JWT", e);
		}
	}

	private static RSAKey toRsaKey(String jwkJson) throws java.text.ParseException {
		final var jwk = JWK.parse(jwkJson);
		if (jwk instanceof RSAKey rsaKey) {
			return rsaKey;
		}
		throw new PeerAuthException("Local private JWK must be an RSA key");
	}

	private static String firstText(String first, String second) {
		return first != null && !first.isBlank() ? first : second;
	}
}
