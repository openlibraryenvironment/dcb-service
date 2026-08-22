package org.olf.dcb.request.lifecycle.ncip.profile.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import io.micronaut.http.client.HttpClient;
import io.micronaut.serde.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.olf.dcb.request.lifecycle.ncip.peerauth.DcbPeerAuthProperties;
import org.olf.dcb.request.lifecycle.ncip.profile.api.DcbProfileRegistrationApi;
import org.olf.dcb.request.lifecycle.ncip.profile.application.DcbProfileRegistrationException;
import org.olf.dcb.request.lifecycle.ncip.profile.application.DcbProfileRegistrationProperties;
import org.olf.dcb.request.lifecycle.ncip.profile.domain.DcbProfileMembership;

class DcbProfileDirectoryPullServiceTests {
	@Test
	void bindsAnExplicitSelectionIntoTheDescriptor() {
		DcbProfileDirectoryPullService service = service(mock(HttpClient.class));

		Map<String, Object> selected = descriptor(service, "OIDC", true);
		Map<String, Object> legacy = descriptor(service, "BASIC/BARCODE+PIN", false);

		assertEquals("OIDC", selected.get("authProfile"));
		assertFalse(legacy.containsKey("authProfile"));
	}

	@Test
	void rejectsAnUninvitedSelectionBeforeDirectoryAccess() {
		HttpClient httpClient = mock(HttpClient.class);
		DcbProfileDirectoryPullService service = service(httpClient);
		DcbProfileMembership invitation = DcbProfileMembership.builder()
			.policy(Map.of(
				"authProfile", "BASIC/BARCODE+PIN",
				"allowedAuthProfiles", List.of("BASIC/BARCODE+PIN", "OIDC")))
			.build();
		var request = new DcbProfileRegistrationApi.RegistrationRequest(
			"https://ors.example/directory",
			"symbol",
			List.of(),
			"SAML",
			"descriptor-hash",
			"idempotency-key");

		DcbProfileRegistrationException exception = assertThrows(
			DcbProfileRegistrationException.class,
			() -> service.validate(invitation, request, "proof"));

		assertEquals("AUTH_PROFILE_NOT_INVITED", exception.code());
		verifyNoInteractions(httpClient);
	}

	@Test
	void treatsAuthenticationProfileAsASensitiveDescriptorChange() {
		DcbProfileDirectoryPullService service = service(mock(HttpClient.class));

		assertTrue(service.sensitiveChanges(
			Map.of("authProfile", "BASIC/BARCODE+PIN"),
			Map.of("authProfile", "OIDC")).contains("authProfile"));
	}

	private static DcbProfileDirectoryPullService service(HttpClient httpClient) {
		return new DcbProfileDirectoryPullService(
			httpClient,
			mock(ObjectMapper.class),
			new DcbProfileRegistrationProperties(),
			new DcbPeerAuthProperties());
	}

	private static Map<String, Object> descriptor(
		DcbProfileDirectoryPullService service,
		String authProfile,
		boolean includeAuthProfile
	) {
		return service.descriptor(
			Map.of(
				"slug", "ors",
				"commonName", "ORS",
				"address", Map.of("input", Map.of("freeform", "Test address"))),
			"symbol",
			"https://ors.example/ncip",
			"https://ors.example/oai",
			Map.of(
				"ncipSystemId", "system",
				"ncipAgencyId", "agency",
				"peerAuthIssuer", "issuer",
				"peerAuthJwksUrl", "https://ors.example/jwks",
				"peerAuthOutboundAudience", "outbound",
				"peerAuthInboundAudience", "inbound",
				"peerAuthSubject", "subject"),
			List.of(),
			authProfile,
			includeAuthProfile);
	}
}
