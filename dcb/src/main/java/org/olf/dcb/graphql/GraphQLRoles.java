package org.olf.dcb.graphql;

import static org.olf.dcb.security.RoleNames.ADMINISTRATOR;
import static org.olf.dcb.security.RoleNames.CONSORTIUM_ADMIN;
import static org.olf.dcb.security.RoleNames.LIBRARY_ADMIN;
import static org.olf.dcb.security.RoleNames.LIBRARY_READ_ONLY;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;

import graphql.schema.DataFetchingEnvironment;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.exceptions.HttpStatusException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The role check a data fetcher applies before it reads or writes administrative data.
 *
 * <h2>Why this is a class and not four more lines in a fetcher</h2>
 *
 * {@code /graphql} is behind {@code isAuthenticated()} and nothing else, so every
 * fetcher decides its own authorisation. A REST controller cannot do that — it carries an
 * explicit {@code @Secured} and {@code ApiSecurityArchitectureTests} fails the build if it
 * does not — but that test reads compiled {@code @Controller} bean definitions and a data
 * fetcher is not one, so the structural guard has a blind side exactly where the
 * authorisation is hand-written.
 *
 * Hand-written once per fetcher, it drifts and it is untestable: {@code DataFetchers} takes
 * thirty-one repositories, so nothing can construct it to assert on the check inside. Here
 * the rule is one method with its own tests, and the fetchers that have adopted it are
 * greppable.
 *
 * <h2>Fail closed</h2>
 *
 * A token with no roles claim at all is refused. "This token does not say who you are" is
 * not the same fact as "this token says you are permitted", and only one of them is a
 * reason to return data.
 */
public final class GraphQLRoles {

	private static final Logger log = LoggerFactory.getLogger(GraphQLRoles.class);

	/**
	 * The roles that administer a DCB instance.
	 *
	 * Deliberately NOT every authenticated principal: the realm also issues
	 * {@code DISCOVERY_SERVICE}, held by discovery backends that may be third party, and
	 * {@code INTERNAL_API}. Neither administers anything, and both would otherwise reach
	 * every unguarded fetcher.
	 */
	public static final Set<String> ADMINISTRATIVE = Set.of(ADMINISTRATOR, CONSORTIUM_ADMIN, LIBRARY_ADMIN);

	/**
	 * The read floor for {@code /graphql}: everyone who administers or observes a DCB
	 * instance, which is {@link #ADMINISTRATIVE} plus read-only staff.
	 *
	 * This is the whole point of the set. {@code LIBRARY_READ_ONLY} exists precisely so
	 * somebody can look without changing anything, and before this it was checked NOWHERE
	 * in the GraphQL layer — which cut both ways: a read-only user could write wherever a
	 * mutation forgot its check, and read-only was never actually granted a read.
	 *
	 * Row-level scoping still applies ON TOP of this, and is the finer instrument:
	 * {@code AgencyAccessScope} restricts a library administrator to their own agencies'
	 * requests and identities. This set answers "may you use the admin API at all", not
	 * "which rows are yours".
	 *
	 * {@code INTERNAL_API}, {@code INTEROP_TESTER} and {@code DISCOVERY_SERVICE} are
	 * absent on purpose. Each is granted on REST controllers by explicit {@code @Secured},
	 * which is where a machine credential's authority is decided and reviewable; none of
	 * them drives an administration UI.
	 */
	public static final Set<String> STAFF = Set.of(ADMINISTRATOR, CONSORTIUM_ADMIN, LIBRARY_ADMIN, LIBRARY_READ_ONLY);

	/**
	 * The roles that administer the consortium rather than a library within it.
	 *
	 * Two kinds of surface need this. First, those that cannot be narrowed by an agency
	 * predicate because they have no agency to narrow on: {@code DataChangeLog} is the case
	 * that forced it, recording every entity in the system keyed on entity name and id, so
	 * a library administrator has no path to their own rows there — only to everybody's.
	 * Prefer a scope predicate wherever the entity can carry one; this is the answer when it
	 * genuinely cannot, not a shortcut around writing the predicate.
	 *
	 * Second, changing how the consortium itself is arranged, as distinct from running a
	 * library within it: the groups, and who is in them. A library administrator edits their
	 * own library; deciding which libraries and agencies belong to which group is a
	 * consortium decision, and it is what the neighbouring {@code createLibrary},
	 * {@code createLocation} and {@code deleteLibrary} fetchers already require.
	 */
	public static final Set<String> CONSORTIUM = Set.of(ADMINISTRATOR, CONSORTIUM_ADMIN);

	private GraphQLRoles() {
	}

	/**
	 * @param fetcher the fetcher name, for the log line — a refusal nobody can attribute
	 *        to a route is a refusal nobody can investigate
	 * @throws HttpStatusException 401 if the caller holds none of {@code permitted}
	 */
	public static void require(DataFetchingEnvironment env, String fetcher, Set<String> permitted) {
		final Collection<String> roles = env.getGraphQlContext().get("roles");

		if (holdsAny(roles, permitted)) {
			return;
		}

		final var user = Optional.ofNullable(env.getGraphQlContext().get("userName"))
			.map(Object::toString)
			.orElse("User not detected");

		log.warn("{}: Access denied for user {} with roles {}: user does not have the required role to perform this action.",
			fetcher, user, roles);

		throw new HttpStatusException(HttpStatus.UNAUTHORIZED,
			"Access denied: you do not have the required role to perform this action.");
	}

	/** Visible for testing: the whole decision, with no environment to build. */
	static boolean holdsAny(@Nullable Collection<String> roles, Set<String> permitted) {
		if (roles == null) {
			return false;
		}

		return roles.stream().anyMatch(permitted::contains);
	}
}
