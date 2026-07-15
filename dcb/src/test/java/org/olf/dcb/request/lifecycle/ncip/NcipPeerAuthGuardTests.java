package org.olf.dcb.request.lifecycle.ncip;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.k_int.peerauth.service.PeerJwksResolver;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.olf.dcb.core.model.DataHostLms;
import org.olf.dcb.request.lifecycle.ncip.peerauth.DcbPeerAuthProperties;
import org.olf.dcb.request.lifecycle.ncip.peerauth.NcipPeerAuth;
import org.olf.dcb.request.lifecycle.ncip.peerauth.NcipPeerAuthGuard;
import reactor.core.publisher.Mono;

class NcipPeerAuthGuardTests {
	@Test
	void acceptsTrustedBearerWhoseSubjectMatchesFromSystemId() throws Exception {
		final var key = new RSAKeyGenerator(2048).keyID("ors-key").generate();
		final var guard = guardFor(new JWKSet(key.toPublicJWK()));
		final var message = new NcipInboundXmlMapper().map(NcipControllerTests.validItemShipped());

		final var problem = guard.problem(
			HttpRequest.POST("/ncip/v2_02", NcipControllerTests.validItemShipped())
				.bearerAuth(token(key, message.hostLmsCode(), NcipPeerAuth.PROTOCOL)),
			message).block();

		assertThat(problem.isEmpty(), is(true));
	}

	@Test
	void rejectsTrustedBearerWhoseSubjectDoesNotMatchFromSystemId() throws Exception {
		final var key = new RSAKeyGenerator(2048).keyID("ors-key").generate();
		final var guard = guardFor(new JWKSet(key.toPublicJWK()));
		final var message = new NcipInboundXmlMapper().map(NcipControllerTests.validItemShipped());

		final var problem = guard.problem(
			HttpRequest.POST("/ncip/v2_02", NcipControllerTests.validItemShipped())
				.bearerAuth(token(key, "other-system", NcipPeerAuth.PROTOCOL)),
			message).block();

		assertThat(problem.orElseThrow().getStatus(), is(HttpStatus.UNAUTHORIZED));
	}

	@Test
	void rejectsTokenWithoutNcipProtocolClaim() throws Exception {
		final var key = new RSAKeyGenerator(2048).keyID("ors-key").generate();
		final var guard = guardFor(new JWKSet(key.toPublicJWK()));
		final var message = new NcipInboundXmlMapper().map(NcipControllerTests.validItemShipped());

		final var problem = guard.problem(
			HttpRequest.POST("/ncip/v2_02", NcipControllerTests.validItemShipped())
				.bearerAuth(token(key, message.hostLmsCode(), "OTHER")),
			message).block();

		assertThat(problem.orElseThrow().getStatus(), is(HttpStatus.UNAUTHORIZED));
	}

	@Test
	void rejectsJwtRequiredPeerWhenSigningInfrastructureIsDisabled() throws Exception {
		final var key = new RSAKeyGenerator(2048).keyID("ors-key").generate();
		final var guard = guardFor(new JWKSet(key.toPublicJWK()), false);
		final var message = new NcipInboundXmlMapper().map(NcipControllerTests.validItemShipped());

		final var problem = guard.problem(
			HttpRequest.POST("/ncip/v2_02", NcipControllerTests.validItemShipped()),
			message).block();

		assertThat(problem.orElseThrow().getStatus(), is(HttpStatus.UNAUTHORIZED));
	}

	private static NcipPeerAuthGuard guardFor(JWKSet jwkSet) {
		return guardFor(jwkSet, true);
	}

	private static NcipPeerAuthGuard guardFor(JWKSet jwkSet, boolean enabled) {
		final var properties = new DcbPeerAuthProperties();
		properties.setEnabled(enabled);
		final var ncip = new DcbPeerAuthProperties.Ncip();
		ncip.setEnabled(enabled);
		properties.setNcip(ncip);
		final var identity = new DcbPeerAuthProperties.LocalIdentity();
		identity.setId("dcb");
		properties.setLocalIdentity(identity);

		final var hostLmsResolver = mock(NcipPeerHostLmsResolver.class);
		when(hostLmsResolver.findBySystemId(any())).thenAnswer(invocation -> Mono.just(DataHostLms.builder()
			.code(invocation.getArgument(0))
			.clientConfig(Map.of(
				"ncip-system-id", invocation.getArgument(0),
				"ncip-peer-auth-mode", "JWT_REQUIRED",
				"ncip-peer-issuer", "https://ors.example/tenant",
				"ncip-peer-jwks-url", "https://ors.example/tenant/jwks",
				"ncip-peer-audience", "ors-appliance"))
			.build()));
		final var resolver = mock(PeerJwksResolver.class);
		when(resolver.resolve(any(), any())).thenReturn(jwkSet);

		return new NcipPeerAuthGuard(
			properties,
			hostLmsResolver,
			resolver,
			new NcipResponseBuilder());
	}

	private static String token(
		com.nimbusds.jose.jwk.RSAKey key,
		String subject,
		String protocol) throws Exception {

		final var now = Instant.now();
		final var jwt = new SignedJWT(
			new JWSHeader.Builder(JWSAlgorithm.RS256)
				.keyID(key.getKeyID())
				.type(JOSEObjectType.JWT)
				.build(),
			new JWTClaimsSet.Builder()
				.issuer("https://ors.example/tenant")
				.subject(subject)
				.audience("dcb")
				.claim("protocol", protocol)
				.issueTime(Date.from(now))
				.expirationTime(Date.from(now.plusSeconds(300)))
				.build());
		jwt.sign(new RSASSASigner(key));
		return jwt.serialize();
	}
}
