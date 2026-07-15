package org.olf.dcb.request.lifecycle.ncip.peerauth;

import static org.olf.dcb.core.interaction.HostLmsPropertyDefinition.stringPropertyDefinition;
import static org.olf.dcb.core.interaction.HostLmsPropertyDefinition.urlPropertyDefinition;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import org.olf.dcb.core.interaction.HostLmsPropertyDefinition;
import org.olf.dcb.core.model.HostLms;

public record NcipPeerAuthProfile(
	Mode mode,
	String issuer,
	URI jwksUri,
	String audience) {

	private static final HostLmsPropertyDefinition MODE = stringPropertyDefinition(
		"ncip-peer-auth-mode", "NCIP peer authentication mode: JWT_REQUIRED or INSECURE", false);
	private static final HostLmsPropertyDefinition ISSUER = stringPropertyDefinition(
		"ncip-peer-issuer", "Approved JWT issuer for this NCIP peer", false);
	private static final HostLmsPropertyDefinition JWKS_URL = urlPropertyDefinition(
		"ncip-peer-jwks-url", "Approved JWKS URL for this NCIP peer", false);
	private static final HostLmsPropertyDefinition AUDIENCE = stringPropertyDefinition(
		"ncip-peer-audience", "JWT audience required by this NCIP peer", false);

	public enum Mode {
		JWT_REQUIRED,
		INSECURE
	}

	public static NcipPeerAuthProfile from(HostLms hostLms) {
		var config = hostLms.getClientConfig();
		Mode mode = parseMode(MODE.getOptionalValueFrom(config, Mode.INSECURE.name()));
		String issuer = ISSUER.getOptionalValueFrom(config, null);
		String jwksUrl = JWKS_URL.getOptionalValueFrom(config, null);
		String audience = AUDIENCE.getOptionalValueFrom(config, null);
		if (mode == Mode.JWT_REQUIRED) {
			require(issuer, ISSUER.getName());
			require(jwksUrl, JWKS_URL.getName());
			require(audience, AUDIENCE.getName());
		}
		return new NcipPeerAuthProfile(
			mode,
			issuer,
			jwksUrl != null ? URI.create(jwksUrl) : null,
			audience);
	}

	public static List<HostLmsPropertyDefinition> settings() {
		return List.of(MODE, ISSUER, JWKS_URL, AUDIENCE);
	}

	public boolean jwtRequired() {
		return mode == Mode.JWT_REQUIRED;
	}

	private static Mode parseMode(String value) {
		try {
			return Mode.valueOf(value.trim().toUpperCase(Locale.ROOT));
		} catch (RuntimeException e) {
			throw new IllegalArgumentException("Unsupported NCIP peer authentication mode: " + value, e);
		}
	}

	private static void require(String value, String property) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(
				"Missing required configuration property for JWT_REQUIRED: \"" + property + "\"");
		}
	}
}
