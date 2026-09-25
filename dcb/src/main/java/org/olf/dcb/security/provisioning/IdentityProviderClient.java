package org.olf.dcb.security.provisioning;

import io.micronaut.serde.annotation.Serdeable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Creating and managing DCB Admin for Libraries accounts at whichever identity provider a
 * deployment runs. One implementation per provider, selected by configuration.
 *
 * <p>Two constraints bind every implementation: <b>create disabled, grant, then enable</b>,
 * so a failure between steps leaves an inert account; and <b>containment is per-provider</b>,
 * so one that cannot express it must say so rather than leave Keycloak's property assumed.
 *
 * <p>Why, and how to prove it: {@code operational:identity-provider-setup.adoc}.
 */
public interface IdentityProviderClient {

	/** Which provider this is, stored on the binding so a migration can tell rows apart. */
	String providerName();

	/**
	 * Create the account, grant the role, enable it, and send the actions email — in that
	 * order, compensating on failure.
	 */
	Mono<ProvisionedUser> provision(ProvisionRequest request);

	/** Enable or disable an existing account. The account is not deleted either way. */
	Mono<Void> setEnabled(String providerUserId, boolean enabled);

	/** Re-send the set-password / verify-email actions link. */
	Mono<Void> sendInvite(String providerUserId);

	/**
	 * The live state of the given accounts.
	 *
	 * <p>Takes the ids DCB already holds rather than querying the provider by agency: DCB's
	 * table is the list of accounts DCB created, and asking the provider "who belongs to
	 * this agency" would also return accounts created outside DCB, which this feature has
	 * no business rendering as though it manages them.
	 */
	Flux<ProvisionedUser> findByIds(Iterable<String> providerUserIds);

	/** What to create. The agency is derived from the library id, never supplied. */
	@Serdeable
	record ProvisionRequest(
		String email,
		String firstName,
		String lastName,
		ProvisionableRole role,
		String agencyCode) {
	}

	/** What came back. No credential field exists on this record. */
	@Serdeable
	record ProvisionedUser(
		String providerUserId,
		String email,
		String firstName,
		String lastName,
		boolean enabled,
		boolean emailVerified) {

		/**
		 * The status DCB records for this provider state.
		 *
		 * <p>Disabled wins over unverified: an account that has been turned off is
		 * disabled whatever it had done before that.
		 */
		public LibraryUserStatus status() {
			if (!enabled) {
				return LibraryUserStatus.DISABLED;
			}

			return emailVerified ? LibraryUserStatus.ACTIVE : LibraryUserStatus.INVITED;
		}
	}
}
