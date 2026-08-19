package org.olf.dcb.graphql;

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
 * The role check a data fetcher applies before it reads administrative data.
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
	public static final Set<String> ADMINISTRATIVE = Set.of("ADMIN", "CONSORTIUM_ADMIN", "LIBRARY_ADMIN");

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
