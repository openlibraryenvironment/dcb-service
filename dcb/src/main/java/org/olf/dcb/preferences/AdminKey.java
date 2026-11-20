package org.olf.dcb.preferences;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import io.micronaut.security.authentication.Authentication;
import jakarta.inject.Singleton;

/**
 * The opaque key a member of staff's preferences are filed under:
 * {@code base64url(HMAC-SHA256(secret, "u:" + sub))}. Never the username, never the subject itself.
 * Changing the secret orphans every stored record.
 */
@Singleton
public class AdminKey {
	private static final String ALGORITHM = "HmacSHA256";
	private static final String USER_SCHEME = "u:";
	private static final String SUBJECT_CLAIM = "sub";

	/** Null when preferences are off; there is no fallback derivation. */
	private final SecretKeySpec key;

	public AdminKey(PreferencesConfig config) {
		this.key = config.isOn()
			? new SecretKeySpec(config.getSecret().getBytes(StandardCharsets.UTF_8), ALGORITHM)
			: null;
	}

	public boolean isAvailable() {
		return key != null;
	}

	public String forCaller(Authentication authentication) {
		if (key == null) {
			throw PreferenceException.unavailable();
		}

		final var subject = authentication == null ? null : authentication.getAttributes().get(SUBJECT_CLAIM);

		if (!(subject instanceof String sub) || sub.isBlank()) {
			throw PreferenceException.noSubject(
				"This token carries no 'sub' claim, so no preference key can be derived for it.");
		}

		return derive(sub);
	}

	String derive(String subject) {
		if (key == null) {
			throw PreferenceException.unavailable();
		}

		try {
			final var mac = Mac.getInstance(ALGORITHM);
			mac.init(key);

			return Base64.getUrlEncoder().withoutPadding()
				.encodeToString(mac.doFinal((USER_SCHEME + subject).getBytes(StandardCharsets.UTF_8)));
		}
		catch (GeneralSecurityException e) {
			throw new IllegalStateException("Cannot derive admin preference key", e);
		}
	}
}
