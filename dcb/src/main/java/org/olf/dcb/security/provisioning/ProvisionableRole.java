package org.olf.dcb.security.provisioning;

import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Collectors;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

/**
 * The only roles this service will ever ask an identity provider to grant.
 *
 * <p>Enforced four times over — this enum, the GraphQL enum of the same name, the
 * {@code library_user_account_role_allowlist} CHECK constraint, and the provider's own
 * {@code map-role} grant, which is the only one that survives a compromise of this service.
 * The value decides what authority gets minted, so no single gate is trusted with it.
 */
@Serdeable
public enum ProvisionableRole {
	LIBRARY_ADMIN,
	LIBRARY_READ_ONLY;

	/**
	 * Parse a client-supplied name, refusing anything not in this enum.
	 *
	 * <p>No hyphen or space translation, deliberately: the values come from a GraphQL enum,
	 * so a value needing repair is a client sending something it was not offered.
	 */
	public static Optional<ProvisionableRole> parse(@Nullable Object value) {
		if (value == null) {
			return Optional.empty();
		}

		return Arrays.stream(values())
			.filter(role -> role.name().equals(value.toString().trim().toUpperCase()))
			.findFirst();
	}

	public static String valid() {
		return Arrays.stream(values())
			.map(Enum::name)
			.collect(Collectors.joining(", "));
	}
}
