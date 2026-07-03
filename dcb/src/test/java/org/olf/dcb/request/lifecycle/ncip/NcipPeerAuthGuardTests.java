package org.olf.dcb.request.lifecycle.ncip;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import com.k_int.peerauth.service.DefaultPeerBindingValidator;
import com.k_int.peerauth.service.NimbusPeerTokenVerifier;
import com.k_int.peerauth.service.TrustedPeerJwksResolver;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.olf.dcb.request.lifecycle.ncip.peerauth.DcbPeerAuthProperties;
import org.olf.dcb.request.lifecycle.ncip.peerauth.DcbPeerAuthStore;
import org.olf.dcb.request.lifecycle.ncip.peerauth.NcipPeerAuth;
import org.olf.dcb.request.lifecycle.ncip.peerauth.NcipPeerAuthGuard;

class NcipPeerAuthGuardTests {
	@Test
	void acceptsTrustedBearerBoundToAssertedNcipSystem() throws Exception {
		final var key = new RSAKeyGenerator(2048)
			.keyID("ors-key")
			.generate();
		final var guard = guardFor(key.toPublicJWK().toJSONObject());
		final var message = new NcipInboundXmlMapper()
			.map(NcipControllerTests.validItemShipped());

		final var problem = guard.problem(
			HttpRequest.POST("/ncip/v2_02", NcipControllerTests.validItemShipped())
				.bearerAuth(token(key, "https://ors.example/tenant", "ors", "dcb")),
			message);

		assertThat(problem.isEmpty(), is(true));
	}

	@Test
	void rejectsTrustedBearerWithUnboundNcipSystem() throws Exception {
		final var key = new RSAKeyGenerator(2048)
			.keyID("ors-key")
			.generate();
		final var guard = guardFor(key.toPublicJWK().toJSONObject());
		final var message = new NcipInboundXmlMapper()
			.map(NcipControllerTests.validAcceptItemResponse());

		final var problem = guard.problem(
			HttpRequest.POST("/ncip/v2_02", NcipControllerTests.validAcceptItemResponse())
				.bearerAuth(token(key, "https://ors.example/tenant", "ors", "dcb")),
			message);

		assertThat(problem.orElseThrow().getStatus(), is(HttpStatus.FORBIDDEN));
	}

	private static NcipPeerAuthGuard guardFor(Map<String, Object> publicJwk) {
		final var properties = new DcbPeerAuthProperties();
		properties.setEnabled(true);
		final var ncip = new DcbPeerAuthProperties.Ncip();
		ncip.setEnabled(true);
		properties.setNcip(ncip);

		final var peer = new DcbPeerAuthProperties.TrustedPeerConfig();
		peer.setPeerId("ors");
		peer.setIssuer("https://ors.example/tenant");
		peer.setSubjects(Set.of("ors"));
		peer.setAudiences(Set.of("dcb"));
		peer.setJwks(Map.of("keys", List.of(publicJwk)));
		final var binding = new DcbPeerAuthProperties.Binding();
		binding.setProtocol(NcipPeerAuth.PROTOCOL);
		binding.setSystemId("supplier-host");
		peer.setBindings(List.of(binding));
		properties.setTrustedPeers(List.of(peer));

		final var store = new DcbPeerAuthStore(properties);
		return new NcipPeerAuthGuard(
			properties,
			new NimbusPeerTokenVerifier(store, new TrustedPeerJwksResolver()),
			new DefaultPeerBindingValidator(store),
			new NcipResponseBuilder());
	}

	private static String token(
		com.nimbusds.jose.jwk.RSAKey key,
		String issuer,
		String subject,
		String audience) throws Exception {

		final var now = Instant.now();
		final var jwt = new SignedJWT(
			new JWSHeader.Builder(JWSAlgorithm.RS256)
				.keyID(key.getKeyID())
				.type(JOSEObjectType.JWT)
				.build(),
			new JWTClaimsSet.Builder()
				.issuer(issuer)
				.subject(subject)
				.audience(audience)
				.issueTime(Date.from(now))
				.expirationTime(Date.from(now.plusSeconds(300)))
				.build());
		jwt.sign(new RSASSASigner(key));
		return jwt.serialize();
	}
}
