package org.olf.dcb.security;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micronaut.context.annotation.Value;
import io.micronaut.core.annotation.Nullable;
import jakarta.inject.Singleton;

/**
 * Which application a token is allowed to drive.
 *
 * <h2>What this is not</h2>
 *
 * It is not a data control, and it does not replace one. {@link org.olf.dcb.graphql.AgencyAccessScope}
 * decides what a caller may READ; this decides which app they may read it through. Both are
 * needed and neither substitutes for the other: DCB Admin for Libraries users hold
 * {@code LIBRARY_ADMIN} by design and call the same {@code /graphql} endpoint, so barring
 * that role from DCB Admin does nothing about the API. Remove the scoping and every library
 * user still reads the consortium with one edited request body.
 *
 * <h2>An unset client id disables the check entirely</h2>
 *
 * Deliberate, and what makes this shippable into an environment that has not split its OIDC
 * clients yet — {@code dcb-admin-ui}'s own {@code .env} has been observed carrying
 * {@code VITE_KEYCLOAK_ID=dcb-admin-for-libraries}, and enforcing against a shared client
 * either locks out every consortium administrator or admits every library user. Hence the
 * WARN phase, and hence {@link #announce()}. The value is a public OIDC client identifier,
 * not a credential.
 *
 * <p>Turning it on: {@code docs/identity-provider-setup.md} §1.3–1.4.
 */
@Singleton
public class AdminUiAccessPolicy {

	private static final Logger log = LoggerFactory.getLogger(AdminUiAccessPolicy.class);

	/** The claim Keycloak and Zitadel both stamp with the client that obtained the token. */
	static final String AUTHORIZED_PARTY = "azp";

	public enum Mode {
		/** Log what would be refused, refuse nothing. */
		WARN,
		/** Refuse. */
		ENFORCE
	}

	private final String adminUiClientId;
	private final Mode mode;

	public AdminUiAccessPolicy(
		@Value("${dcb.security.admin-ui.client-id:}") @Nullable String adminUiClientId,
		@Value("${dcb.security.admin-ui.mode:WARN}") String mode) {

		this.adminUiClientId = (adminUiClientId == null || adminUiClientId.isBlank())
			? null
			: adminUiClientId.trim();

		this.mode = Mode.valueOf(mode.trim().toUpperCase());
	}

	/**
	 * Say at boot whether this control is armed.
	 *
	 * <p>An access control that can be silently disabled by a missing environment variable
	 * must announce which state it is in, or the first time anybody finds out is when it
	 * fails to stop something. "No refusals in the log" and "the check never ran" look
	 * identical from the outside, and only one of them is safe.
	 */
	@jakarta.annotation.PostConstruct
	void announce() {
		if (!isConfigured()) {
			log.info("DCB Admin access bar is OFF: dcb.security.admin-ui.client-id is not set");
			return;
		}

		log.info("DCB Admin access bar is {} for client {}", mode, adminUiClientId);
	}

	/** True when the check is configured at all. */
	public boolean isConfigured() {
		return adminUiClientId != null;
	}

	public boolean isEnforcing() {
		return mode == Mode.ENFORCE;
	}

	/**
	 * Why this caller may not drive DCB Admin, or empty when they may.
	 *
	 * <p>Permitted, deliberately, when:
	 * <ul>
	 *   <li>no client id is configured - the check is off;</li>
	 *   <li>the token carries no {@code azp} at all. A legacy or machine token must not
	 *       be broken by a rule about a browser application, and absence of the claim is
	 *       not evidence the caller is using DCB Admin;</li>
	 *   <li>the token was minted for some other client - DAFL's, or a discovery app's.
	 *       This rule is about one application, not about roles in general.</li>
	 * </ul>
	 */
	public Optional<String> refusalReason(@Nullable Collection<String> roles,
		@Nullable Map<String, Object> attributes) {

		if (!isConfigured()) {
			return Optional.empty();
		}

		final var authorizedParty = stringAttribute(attributes, AUTHORIZED_PARTY);

		if (authorizedParty.isEmpty() || !adminUiClientId.equals(authorizedParty.get())) {
			return Optional.empty();
		}

		final var scope = CallerScope.from(roles, attributes);

		if (scope.consortiumWide()) {
			return Optional.empty();
		}

		return Optional.of("DCB Admin is a consortium-level tool and this account holds no "
			+ "consortium role");
	}

	/**
	 * Apply the policy, returning the message to refuse with, or empty to proceed.
	 *
	 * <p>In {@code WARN} the refusal is logged and nothing is refused, so the log can be
	 * drained before the switch is flipped. The log line names the user and the roles
	 * because a refusal nobody can attribute to a caller is a refusal nobody can
	 * investigate - and it names neither a token nor a claim value beyond the client id,
	 * which is public.
	 */
	public Optional<String> enforce(@Nullable String user, @Nullable Collection<String> roles,
		@Nullable Map<String, Object> attributes) {

		final var refusal = refusalReason(roles, attributes);

		if (refusal.isEmpty()) {
			return Optional.empty();
		}

		if (!isEnforcing()) {
			log.warn("Would deny DCB Admin access: user={} roles={} client={} reason={}",
				user, roles, adminUiClientId, refusal.get());

			return Optional.empty();
		}

		log.warn("Denied DCB Admin access: user={} roles={} client={} reason={}",
			user, roles, adminUiClientId, refusal.get());

		return refusal;
	}

	private static Optional<String> stringAttribute(@Nullable Map<String, Object> attributes,
		String name) {

		return attributes == null
			? Optional.empty()
			: Optional.ofNullable(attributes.get(name))
				.filter(String.class::isInstance)
				.map(String.class::cast)
				.filter(value -> !value.isBlank());
	}
}
