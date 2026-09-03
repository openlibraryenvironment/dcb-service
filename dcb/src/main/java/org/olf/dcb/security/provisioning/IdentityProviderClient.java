package org.olf.dcb.security.provisioning;

import io.micronaut.serde.annotation.Serdeable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Creating and managing DCB Admin for Libraries accounts at whichever identity provider a
 * deployment runs. One implementation per provider, selected by configuration.
 *
 * <p>Two constraints bind every implementation.
 *
 * <p><b>Create disabled, grant the role, then enable.</b> A failure between steps must leave
 * an inert account. Create-enabled-then-grant leaves an ACTIVE account whose role assignment
 * failed, and role absence stops meaning access absence the moment anything infers from it.
 *
 * <p><b>Containment is per-provider, so each implementation states its own.</b> The controls
 * in {@link ProvisionableRole} all live inside this service and fall together with it; only
 * the provider's own grant survives a compromise. An implementation that cannot express one
 * must say so rather than leave the Keycloak property assumed.
 *
 * <p>Provider setup, the containment grant and how to prove it:
 * {@code docs/identity-provider-setup.md}.
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
