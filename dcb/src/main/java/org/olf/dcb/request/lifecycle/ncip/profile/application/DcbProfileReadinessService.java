package org.olf.dcb.request.lifecycle.ncip.profile.application;

import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.RSAKey;
import jakarta.inject.Singleton;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import org.olf.dcb.request.lifecycle.ncip.NcipIdentityConfiguration;
import org.olf.dcb.request.lifecycle.ncip.peerauth.DcbPeerAuthProperties;
import org.olf.dcb.request.lifecycle.ncip.profile.api.DcbProfileRegistrationApi;

@Singleton
public class DcbProfileReadinessService {
	private static final String PASS = "PASS";
	private static final String FAIL = "FAIL";

	private final DcbProfileRegistrationProperties registrationProperties;
	private final DcbPeerAuthProperties peerAuthProperties;
	private final NcipIdentityConfiguration ncipIdentity;

	public DcbProfileReadinessService(
		DcbProfileRegistrationProperties registrationProperties,
		DcbPeerAuthProperties peerAuthProperties,
		NcipIdentityConfiguration ncipIdentity
	) {
		this.registrationProperties = registrationProperties;
		this.peerAuthProperties = peerAuthProperties;
		this.ncipIdentity = ncipIdentity;
	}

	public DcbProfileRegistrationApi.ReadinessResponse readiness() {
		List<DcbProfileRegistrationApi.ReadinessCheck> checks = new ArrayList<>();
		DcbPeerAuthProperties.LocalIdentity identity = peerAuthProperties.getLocalIdentity();

		checks.add(check(
			"PROFILE_NODE_NAME",
			hasText(registrationProperties.getNodeName()),
			"The DCB profile-registration node name is configured.",
			"Configure dcb.profile-registration.node-name."));
		checks.add(check(
			"PUBLIC_BASE_URL",
			validPublicUri(registrationProperties.getPublicBaseUrl()),
			"The public DCB base URL is valid.",
			"Configure dcb.profile-registration.public-base-url with the externally reachable HTTPS DCB URL."));
		checks.add(check(
			"PEER_AUTH_ENABLED",
			peerAuthProperties.isEnabled(),
			"DCB peer authentication is enabled.",
			"Enable dcb.peer-auth.enabled."));
		checks.add(check(
			"NCIP_PEER_AUTH_ENABLED",
			peerAuthProperties.isNcipEnabled(),
			"NCIP peer authentication is enabled.",
			"Enable dcb.peer-auth.ncip.enabled."));
		checks.add(check(
			"PEER_IDENTITY",
			validIdentity(identity),
			"The local peer identity is complete.",
			"Configure the local peer ID, issuer, subject, audience, and key ID."));
		checks.add(check(
			"PEER_JWKS_URL",
			validPublicUri(identity.getJwksUri()),
			"The peer JWKS URL is valid.",
			"Configure dcb.peer-auth.local-identity.jwks-uri with an externally reachable HTTPS URL."));
		checks.add(check(
			"PEER_SIGNING_KEY",
			validSigningKey(identity),
			"The active RSA signing key and public JWK match.",
			"Configure a matching 2048-bit or stronger RSA public/private JWK pair using the active key ID."));
		checks.add(check(
			"NCIP_IDENTITY",
			validNcipIdentity(),
			"The DCB NCIP system and agency IDs are configured.",
			"Configure dcb.ncip.system-id and dcb.ncip.agency-id."));

		boolean ready = checks.stream().allMatch(check -> PASS.equals(check.status()));
		return new DcbProfileRegistrationApi.ReadinessResponse(
			ready,
			DcbProfileRegistrationApi.PROFILE_ID,
			DcbProfileRegistrationApi.PROFILE_VERSION,
			registrationProperties.getPublicBaseUrl() != null
				? trimSlash(registrationProperties.getPublicBaseUrl().toString())
				: null,
			checks
		);
	}

	public void requireReady() {
		if (!readiness().ready()) {
			throw DcbProfileRegistrationException.notReady(
				"PROFILE_REGISTRATION_NOT_READY",
				"DCB Profile NCIP2.02+ onboarding prerequisites are incomplete.");
		}
	}

	private DcbProfileRegistrationApi.ReadinessCheck check(
		String code,
		boolean passed,
		String successMessage,
		String remediation
	) {
		return new DcbProfileRegistrationApi.ReadinessCheck(
			code,
			passed ? PASS : FAIL,
			passed ? successMessage : remediation,
			passed ? null : remediation
		);
	}

	private boolean validIdentity(DcbPeerAuthProperties.LocalIdentity identity) {
		return hasText(identity.getId())
			&& hasText(identity.getIssuer())
			&& hasText(identity.getSubject())
			&& identity.getAudiences() != null
			&& identity.getAudiences().stream().anyMatch(DcbProfileReadinessService::hasText)
			&& hasText(identity.getKeyId());
	}

	private boolean validSigningKey(DcbPeerAuthProperties.LocalIdentity identity) {
		if (!hasText(identity.getKeyId())
			|| !hasText(identity.getPublicJwk())
			|| !hasText(identity.getPrivateJwk())) {
			return false;
		}
		try {
			JWK publicJwk = JWK.parse(identity.getPublicJwk());
			JWK privateJwk = JWK.parse(identity.getPrivateJwk());
			if (!(publicJwk instanceof RSAKey publicRsa)
				|| !(privateJwk instanceof RSAKey privateRsa)
				|| publicRsa.isPrivate()
				|| !privateRsa.isPrivate()
				|| privateRsa.size() < 2048) {
				return false;
			}
			return identity.getKeyId().equals(publicRsa.getKeyID())
				&& identity.getKeyId().equals(privateRsa.getKeyID())
				&& publicRsa.getModulus().equals(privateRsa.getModulus())
				&& publicRsa.getPublicExponent().equals(privateRsa.getPublicExponent());
		}
		catch (Exception ignored) {
			return false;
		}
	}

	private boolean validNcipIdentity() {
		try {
			return hasText(ncipIdentity.getSystemId()) && hasText(ncipIdentity.getAgencyId());
		}
		catch (IllegalStateException ignored) {
			return false;
		}
	}

	private boolean validPublicUri(URI uri) {
		if (uri == null || !uri.isAbsolute() || !hasText(uri.getHost())) {
			return false;
		}
		return "https".equalsIgnoreCase(uri.getScheme())
			|| (registrationProperties.isAllowHttp() && "http".equalsIgnoreCase(uri.getScheme()));
	}

	private static boolean hasText(String value) {
		return value != null && !value.isBlank();
	}

	private static String trimSlash(String value) {
		return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
	}
}
