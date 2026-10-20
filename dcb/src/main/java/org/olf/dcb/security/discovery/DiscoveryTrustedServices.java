package org.olf.dcb.security.discovery;

import java.net.URI;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.nimbusds.jose.util.JSONArrayUtils;

import io.micronaut.core.annotation.Nullable;

/**
 * Reads {@code dcb.discovery.trusted-services-json}. A list of objects cannot be expressed as
 * environment variables (an index stays a dot segment, which list binding does not read), so the
 * whole list is one JSON value, with the same kebab-case keys as the YAML would use.
 */
final class DiscoveryTrustedServices {
	static final String PROPERTY = DiscoveryConfig.ROOT + ".trusted-services-json";

	private DiscoveryTrustedServices() { }

	/**
	 * @throws IllegalArgumentException for a blank value, malformed JSON, an entry that is not an
	 *         object, or a field that is present but blank
	 */
	static List<DiscoveryTrustedService> parse(@Nullable String json) {
		if (json == null) {
			return List.of();
		}

		if (json.isBlank()) {
			throw new IllegalArgumentException(PROPERTY + " is set but blank. Remove it, "
				+ "or give a JSON array of trusted services");
		}

		final List<Object> entries;

		try {
			// Nimbus is already a dependency for JWT verification, and its parser returns plain maps
			entries = JSONArrayUtils.parse(json);
		} catch (ParseException e) {
			throw new IllegalArgumentException(PROPERTY + " is not a JSON array: " + e.getMessage(), e);
		}

		final var parsed = new ArrayList<DiscoveryTrustedService>();

		for (final var entry : entries) {
			if (!(entry instanceof Map<?, ?> fields)) {
				throw new IllegalArgumentException(PROPERTY + " entries must be JSON objects, found: " + entry);
			}

			parsed.add(new DiscoveryTrustedService(text(fields, "service-id"), text(fields, "issuer"),
				jwksUri(fields), text(fields, "client-id"), jwks(fields)));
		}

		return parsed;
	}

	@Nullable
	private static URI jwksUri(Map<?, ?> fields) {
		final var jwksUri = text(fields, "jwks-uri");

		if (jwksUri == null) {
			return null;
		}

		try {
			return URI.create(jwksUri);
		} catch (IllegalArgumentException e) {
			throw new IllegalArgumentException(PROPERTY + " has an unusable jwks-uri: " + jwksUri, e);
		}
	}

	@Nullable
	private static Map<String, Object> jwks(Map<?, ?> fields) {
		if (!(fields.get("jwks") instanceof Map<?, ?> jwks)) {
			return null;
		}

		final var typed = new LinkedHashMap<String, Object>();
		jwks.forEach((key, value) -> typed.put(String.valueOf(key), value));
		return typed;
	}

	@Nullable
	private static String text(Map<?, ?> fields, String key) {
		final var value = fields.get(key);

		if (value == null) {
			return null;
		}

		if (!(value instanceof String text) || text.isBlank()) {
			throw new IllegalArgumentException(PROPERTY + ": \"" + key + "\" must be a non-blank string, or left out");
		}

		return text;
	}
}
