package org.olf.dcb.security;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import io.micronaut.core.annotation.Nullable;

/**
 * The agencies a token claims, read one way, in ONE PLACE.
 *
 * Both the GraphQL fetchers (via {@code AgencyScopeResolver}) and the Insights
 * endpoints (via {@link CallerScope}) ask this of the same token. Two readings would
 * scope the same user differently in two apps, and nothing would fail to say so.
 *
 * <b>Empty is not "unrestricted"</b> - it means the token says nothing, and every
 * consumer must treat that as no access. Why {@code code} rather than a new claim,
 * and why multi-valued: docs/insights.md part 2.
 */
public final class AgencyClaims {

	/** The claim DCB Admin for Libraries issues, and the GraphQL path already reads. */
	public static final String CODE = "code";

	/** Accepted alongside {@link #CODE} for providers that cannot issue it multi-valued. */
	public static final String AGENCY_CODES = "agencyCodes";

	private AgencyClaims() {
	}

	/**
	 * The agency codes this token carries, in the order the claims present them and
	 * without duplicates. Never null; empty when the token says nothing.
	 */
	public static Collection<String> from(@Nullable Map<String, Object> attributes) {
		final Set<String> codes = new LinkedHashSet<>();

		if (attributes != null) {
			addCodes(codes, attributes.get(CODE));
			addCodes(codes, attributes.get(AGENCY_CODES));
		}

		return new ArrayList<>(codes);
	}

	/**
	 * Tolerates a single string or a list. A blank value is absent, not an agency - an
	 * empty Keycloak attribute is the shape a half-finished backfill takes.
	 */
	private static void addCodes(Set<String> target, @Nullable Object value) {
		if (value instanceof Collection<?> collection) {
			collection.forEach(element -> addCode(target, element));
		}
		else {
			addCode(target, value);
		}
	}

	private static void addCode(Set<String> target, @Nullable Object value) {
		if (value instanceof String code && !code.isBlank()) {
			target.add(code.trim());
		}
	}
}
