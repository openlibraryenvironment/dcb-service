package org.olf.dcb.security;

import static java.util.stream.Collectors.toUnmodifiableSet;

import java.util.List;
import java.util.Set;

import io.micronaut.context.annotation.Value;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.security.authentication.Authentication;
import jakarta.inject.Singleton;

/**
 * The OIDC clients staff place requests through: DCB Admin and DCB Admin for Libraries. A request
 * placed with a token issued to one of them was placed by staff. Roles cannot say so, because the
 * discovery services that place patrons' requests hold admin roles too.
 */
@Singleton
public class StaffClients {
	private final Set<String> clientIds;

	public StaffClients(@Value("${dcb.requests.staff-client-ids:}") @Nullable List<String> clientIds) {
		this.clientIds = clientIds == null
			? Set.of()
			: clientIds.stream()
				.map(String::trim)
				.filter(clientId -> !clientId.isEmpty())
				.collect(toUnmodifiableSet());
	}

	public boolean isConfigured() {
		return !clientIds.isEmpty();
	}

	public Set<String> clientIds() {
		return clientIds;
	}

	public boolean issuedTo(@Nullable Authentication authentication) {
		if (authentication == null) {
			return false;
		}

		return authentication.getAttributes().get(AdminUiAccessPolicy.AUTHORIZED_PARTY) instanceof String client
			&& clientIds.contains(client);
	}
}
