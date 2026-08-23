package org.olf.dcb.request.lifecycle.ncip.profile.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.BlockingHttpClient;
import io.micronaut.http.client.HttpClient;
import io.micronaut.serde.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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

	@Test
	void fetchesTheBoundedSelfViewWithoutChangingTheCanonicalDirectoryUrl() throws Exception {
		HttpClient httpClient = mock(HttpClient.class);
		BlockingHttpClient blockingClient = mock(BlockingHttpClient.class);
		ObjectMapper objectMapper = mock(ObjectMapper.class);
		when(httpClient.toBlocking()).thenReturn(blockingClient);
		when(blockingClient.retrieve(any(HttpRequest.class), eq(Argument.of(String.class))))
			.thenReturn("{}");
		when(objectMapper.readValue("{}", Map.class)).thenReturn(directoryPage());
		when(objectMapper.writeValueAsBytes(any())).thenReturn("{}".getBytes(StandardCharsets.UTF_8));
		DcbProfileRegistrationProperties properties = new DcbProfileRegistrationProperties();
		properties.setAllowHttp(true);
		properties.setAllowPrivateAddresses(true);
		DcbProfileDirectoryPullService service = new DcbProfileDirectoryPullService(
			httpClient,
			objectMapper,
			properties,
			new DcbPeerAuthProperties());
		String canonicalUrl = "http://127.0.0.1:8080/directory?pageno=4&self=false";
		DcbProfileMembership membership = DcbProfileMembership.builder()
			.remoteDirectoryUrl(canonicalUrl)
			.selectedSymbol("ORS:TEST")
			.policy(Map.of(
				"hostLmsCode", "ors-test",
				"borrowingAllowed", false,
				"supplyingAllowed", false))
			.approvedDescriptor(Map.of("locations", List.of()))
			.build();

		DcbProfileDirectoryPullService.ValidatedRegistration result = service.pull(membership);

		ArgumentCaptor<HttpRequest<?>> request = ArgumentCaptor.forClass(HttpRequest.class);
		verify(blockingClient).retrieve(request.capture(), eq(Argument.of(String.class)));
		assertEquals("true", request.getValue().getParameters().get("self"));
		assertEquals("4", request.getValue().getParameters().get("pageno"));
		assertEquals(canonicalUrl, result.directoryUrl());
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

	private static Map<String, Object> directoryPage() {
		Map<String, Object> symbol = Map.of("authority", "ORS", "code", "TEST");
		List<Map<String, Object>> enabledForSymbols = List.of(symbol);
		Map<String, Object> ncip = Map.of(
			"type", "NCIP2",
			"serviceAddress", "http://127.0.0.1:8080/ncip",
			"authMechanism", "JWT_REQUIRED",
			"enabledForSymbols", enabledForSymbols,
			"config", Map.of(
				"profile", DcbProfileRegistrationApi.PROFILE_ID,
				"profileVersion", DcbProfileRegistrationApi.PROFILE_VERSION,
				"ncipSystemId", "fallback",
				"ncipAgencyId", "ORS:TEST",
				"peerAuthIssuer", "http://127.0.0.1:8080",
				"peerAuthJwksUrl", "http://127.0.0.1:8080/.well-known/jwks.json",
				"peerAuthOutboundAudience", "dcb",
				"peerAuthInboundAudience", "orsa",
				"peerAuthSubject", "fallback"));
		Map<String, Object> oai = Map.of(
			"type", "OAI-PMH",
			"serviceAddress", "http://127.0.0.1:8080/oai",
			"enabledForSymbols", enabledForSymbols,
			"config", Map.of("metadataPrefixes", List.of("marcxml")));
		return Map.of("content", List.of(Map.of(
			"isSelf", true,
			"isPublic", true,
			"slug", "ors-test",
			"commonName", "ORS Test",
			"address", Map.of("input", Map.of("freeform", "Test address")),
			"symbols", List.of(symbol),
			"services", List.of(ncip, oai),
			"locations", List.of())));
	}
}
