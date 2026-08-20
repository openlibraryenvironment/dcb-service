package org.olf.dcb.security;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import io.micronaut.core.annotation.Nullable;

/**
 * What this caller is allowed to see. Derived once, at the security boundary, from the
 * validated token - never from a GraphQL argument or a query parameter.
 *
 * {@code agencyCodes} is a COLLECTION because a person can administer several libraries;
 * reading it as a scalar silently refuses exactly those people. Empty means "all" for a
 * consortium-level caller and "unauthorised" for a library-level one - never "unrestricted".
 */
public record CallerScope(boolean consortiumWide, Collection<String> agencyCodes, boolean libraryScoped) {

	public CallerScope {
		agencyCodes = agencyCodes == null ? List.of() : List.copyOf(agencyCodes);
	}

	public static CallerScope from(@Nullable Collection<String> roles,
		@Nullable Map<String, Object> attributes) {

		final var safeRoles = roles == null ? List.<String>of() : roles;

		final boolean consortium = safeRoles.contains(RoleNames.ADMINISTRATOR)
			|| safeRoles.contains(RoleNames.CONSORTIUM_ADMIN)
			|| safeRoles.contains(RoleNames.INTERNAL_API);

		final boolean library = safeRoles.contains(RoleNames.LIBRARY_ADMIN)
			|| safeRoles.contains(RoleNames.LIBRARY_READ_ONLY);

		return new CallerScope(consortium, AgencyClaims.from(attributes), library);
	}

	/** True when this caller must have a server-side predicate applied. */
	public boolean requiresNarrowing() {
		return !consortiumWide && libraryScoped;
	}

	/** A library-scoped caller with no agency in the token. Fail closed. */
	public boolean isIncoherent() {
		return requiresNarrowing() && agencyCodes.isEmpty();
	}
}
