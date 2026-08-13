package org.olf.dcb.request.lifecycle.ncip.profile.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import io.micronaut.http.HttpStatus;
import java.net.URI;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.olf.dcb.request.lifecycle.ncip.NcipIdentityConfiguration;
import org.olf.dcb.request.lifecycle.ncip.peerauth.DcbPeerAuthProperties;

class DcbProfileReadinessServiceTests {
	@Test
	void reportsReadyWhenEveryOnboardingDependencyIsConfigured() throws Exception {
		DcbProfileReadinessService service = configuredService("https://dcb.example");

		var response = service.readiness();

		assertTrue(response.ready());
		assertEquals("https://dcb.example", response.dcbBaseUrl());
		assertEquals(8, response.checks().size());
		assertTrue(response.checks().stream().allMatch(check -> "PASS".equals(check.status())));
	}

	@Test
	void reportsAllMissingConfigurationWithoutExposingSecrets() {
		DcbProfileRegistrationProperties registration = new DcbProfileRegistrationProperties();
		registration.setPublicBaseUrl(URI.create("https://dcb.example"));
		DcbPeerAuthProperties peerAuth = new DcbPeerAuthProperties();
		DcbPeerAuthProperties.LocalIdentity identity = new DcbPeerAuthProperties.LocalIdentity();
		identity.setPrivateJwk("private-secret");
		identity.setPublicJwk("public-secret");
		peerAuth.setLocalIdentity(identity);
		DcbProfileReadinessService service = new DcbProfileReadinessService(
			registration,
			peerAuth,
			new NcipIdentityConfiguration());

		var response = service.readiness();
		String serializedShape = response.toString();

		assertFalse(response.ready());
		assertTrue(response.checks().stream().anyMatch(check ->
			"PEER_SIGNING_KEY".equals(check.code()) && "FAIL".equals(check.status())));
		assertFalse(serializedShape.contains("private-secret"));
		assertFalse(serializedShape.contains("public-secret"));

		DcbProfileRegistrationException exception = assertThrows(
			DcbProfileRegistrationException.class,
			service::requireReady);
		assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exception.status());
		assertEquals("PROFILE_REGISTRATION_NOT_READY", exception.code());
		assertTrue(exception.retryable());
	}

	@Test
	void permitsHttpOnlyWhenTheExplicitDevelopmentOverrideIsEnabled() throws Exception {
		DcbProfileRegistrationProperties registration = configuredRegistration("http://dcb:8080");
		ConfiguredPeerAuth configuredPeerAuth = configuredPeerAuth("http://dcb:8080");
		NcipIdentityConfiguration ncipIdentity = configuredNcipIdentity();

		DcbProfileReadinessService secureService = new DcbProfileReadinessService(
			registration,
			configuredPeerAuth.properties(),
			ncipIdentity);
		assertFalse(secureService.readiness().ready());

		registration.setAllowHttp(true);
		DcbProfileReadinessService developmentService = new DcbProfileReadinessService(
			registration,
			configuredPeerAuth.properties(),
			ncipIdentity);
		assertTrue(developmentService.readiness().ready());
	}

	private static DcbProfileReadinessService configuredService(String baseUrl) throws Exception {
		ConfiguredPeerAuth configuredPeerAuth = configuredPeerAuth(baseUrl);
		return new DcbProfileReadinessService(
			configuredRegistration(baseUrl),
			configuredPeerAuth.properties(),
			configuredNcipIdentity());
	}

	private static DcbProfileRegistrationProperties configuredRegistration(String baseUrl) {
		DcbProfileRegistrationProperties registration = new DcbProfileRegistrationProperties();
		registration.setNodeName("Test DCB");
		registration.setPublicBaseUrl(URI.create(baseUrl));
		return registration;
	}

	private static ConfiguredPeerAuth configuredPeerAuth(String baseUrl) throws Exception {
		var key = new RSAKeyGenerator(2048).keyID("dcb-key").generate();
		DcbPeerAuthProperties properties = new DcbPeerAuthProperties();
		properties.setEnabled(true);
		DcbPeerAuthProperties.Ncip ncip = new DcbPeerAuthProperties.Ncip();
		ncip.setEnabled(true);
		properties.setNcip(ncip);
		DcbPeerAuthProperties.LocalIdentity identity = new DcbPeerAuthProperties.LocalIdentity();
		identity.setId("dcb");
		identity.setIssuer(baseUrl + "/peer-auth");
		identity.setSubject("DCB:TEST");
		identity.setAudiences(Set.of("ors-appliance"));
		identity.setJwksUri(URI.create(baseUrl + "/peer-auth/.well-known/jwks.json"));
		identity.setKeyId(key.getKeyID());
		identity.setPublicJwk(key.toPublicJWK().toJSONString());
		identity.setPrivateJwk(key.toJSONString());
		properties.setLocalIdentity(identity);
		return new ConfiguredPeerAuth(properties);
	}

	private static NcipIdentityConfiguration configuredNcipIdentity() {
		NcipIdentityConfiguration ncipIdentity = new NcipIdentityConfiguration();
		ncipIdentity.setSystemId("DCB:TEST");
		ncipIdentity.setAgencyId("DCB:TEST");
		return ncipIdentity;
	}

	private record ConfiguredPeerAuth(DcbPeerAuthProperties properties) {
	}
}
