package org.olf.dcb.request.lifecycle.ncip.peerauth;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.SignedJWT;
import io.micronaut.http.HttpRequest;
import java.net.URI;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.olf.dcb.core.model.DataHostLms;
import org.olf.dcb.request.lifecycle.ncip.NcipIdentityConfiguration;

class NcipPeerAuthorizationServiceTests {
	@Test
	void signsOutboundNcipRequestWhenPeerAuthIsEnabled() throws Exception {
		final var key = new RSAKeyGenerator(2048)
			.keyID("dcb-key")
			.generate();
		final var properties = new DcbPeerAuthProperties();
		properties.setEnabled(true);
		final var ncip = new DcbPeerAuthProperties.Ncip();
		ncip.setEnabled(true);
		properties.setNcip(ncip);
		final var identity = new DcbPeerAuthProperties.LocalIdentity();
		identity.setId("dcb");
		identity.setIssuer("https://dcb.example");
		identity.setSubject("dcb");
		identity.setAudiences(Set.of("ors"));
		identity.setKeyId(key.getKeyID());
		identity.setJwksUri(URI.create("https://dcb.example/peer-auth/.well-known/jwks.json"));
		identity.setPublicJwk(key.toPublicJWK().toJSONString());
		identity.setPrivateJwk(key.toJSONString());
		properties.setLocalIdentity(identity);
		final var store = new DcbPeerAuthStore(properties);
		final var service = new NcipPeerAuthorizationService(
			properties,
			new NimbusPeerTokenSigner(store),
			ncipIdentityConfiguration());

		final var request = service.authorize(
			HttpRequest.POST("https://ors.example/ncip/v2_02", "<NCIPMessage/>"),
			DataHostLms.builder().code("ors-host").build());

		final var authorization = request.getHeaders()
			.getAuthorization()
			.orElseThrow();
		final var jwt = SignedJWT.parse(authorization.substring("Bearer ".length()));

		assertThat(jwt.getJWTClaimsSet().getIssuer(), is("https://dcb.example"));
		assertThat(jwt.getJWTClaimsSet().getSubject(), is("dcb"));
		assertThat(jwt.getJWTClaimsSet().getAudience(), is(List.of("ors")));
		assertThat(jwt.getJWTClaimsSet().getStringClaim("protocol"), is(NcipPeerAuth.PROTOCOL));
		assertThat(jwt.getJWTClaimsSet().getStringClaim("systemId"), is("dcb-system"));
	}

	private static NcipIdentityConfiguration ncipIdentityConfiguration() {
		final var configuration = new NcipIdentityConfiguration();
		configuration.setSystemId("dcb-system");
		configuration.setAgencyId("dcb-agency");
		return configuration;
	}
}
