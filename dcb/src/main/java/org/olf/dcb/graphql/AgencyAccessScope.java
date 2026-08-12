package org.olf.dcb.graphql;

import static org.olf.dcb.graphql.GraphQLSecurityContextCustomizer.AGENCY_CODES;
import static services.k_int.data.querying.lucene.LuceneFieldQueryNodeBuilder.QUERY_PATHS;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.olf.dcb.core.model.PatronRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import graphql.schema.DataFetchingEnvironment;
import io.micronaut.data.repository.jpa.criteria.QuerySpecification;

/**
 * Which libraries' data a GraphQL request is allowed to see.
 * <p>
 * The patron request query used to run whatever filter the client sent, unmodified.
 * DCB Admin for Libraries scoped its own reads to the user's library, but that was a
 * convention in the caller rather than a control: the filter could simply be dropped.
 * On a shared system it was not even the right convention - the borrowing grid
 * filtered on the patron's Host LMS, which is every co-tenant library rather than
 * one, so sixty libraries on a single Koha could read each other's requests.
 * <p>
 * <strong>Roles decide whether a scope applies; the claim decides what it contains.</strong>
 * Both matter, and conflating them breaks one product or the other. DCB Admin users
 * carry an agency claim too - they are people at libraries - so scoping on the claim
 * alone would cut consortium staff off from the consortium. Scoping on role alone
 * would leave library users unrestricted, which is the problem being fixed.
 */
public final class AgencyAccessScope {
	private static final Logger log = LoggerFactory.getLogger(AgencyAccessScope.class);

	/**
	 * Roles that see the whole consortium regardless of which agency they belong to.
	 * <p>
	 * Deliberately not LIBRARY_ADMIN. That role administers a library, not the
	 * consortium, and it is the role DCB Admin for Libraries issues.
	 */
	private static final Set<String> UNRESTRICTED_ROLES = Set.of("ADMIN", "CONSORTIUM_ADMIN");

	private AgencyAccessScope() {
	}

	/**
	 * Narrow a patron request query to the agencies this user may see.
	 *
	 * @param specification the filter the client asked for, or null for "everything"
	 * @return the specification to actually run
	 */
	public static QuerySpecification<PatronRequest> restrict(DataFetchingEnvironment env,
		QuerySpecification<PatronRequest> specification) {

		if (isUnrestricted(env)) {
			return specification;
		}

		final var visible = visibleAgencyCodes(env);

		if (visible.isEmpty()) {
			// Not a consortium role and no agency claim. Returning everything here would
			// make the whole control decorative, so this is a deliberate closed failure -
			// it should be read as "this token does not say who you are".
			log.warn("Patron request query from a caller with neither a consortium role "
				+ "nor an agency claim; returning nothing");
		}

		final var permitted = forAgencies(visible);

		return specification == null ? permitted : specification.and(permitted);
	}

	/**
	 * Requests a set of agencies may see: the ones they borrowed, and the ones they
	 * supplied.
	 * <p>
	 * Both sides, because the two are different screens over the same query - the
	 * borrowing grid filters on the patron's agency and the supplying grid on the
	 * supplier's, and a scope covering only one would empty the other.
	 * <p>
	 * Pickup is not included. Under pickup-anywhere a third library handles the item
	 * and has a fair claim to see the request, but PatronRequest records the pickup
	 * location as a bare identifier rather than an association, so there is no path to
	 * join. No screen depends on it today; it needs the relation before it can be
	 * added, not a cleverer predicate.
	 */
	private static QuerySpecification<PatronRequest> forAgencies(Collection<String> agencyCodes) {
		final QuerySpecification<PatronRequest> borrowed
			= QUERY_PATHS.get("patronAgencyCode").isAnyOf(agencyCodes);

		final QuerySpecification<PatronRequest> supplied
			= QUERY_PATHS.get("supplyingAgencyCode").isAnyOf(agencyCodes);

		return borrowed.or(supplied);
	}

	public static boolean isUnrestricted(DataFetchingEnvironment env) {
		return rolesOf(env).stream().anyMatch(UNRESTRICTED_ROLES::contains);
	}

	/**
	 * The agencies named by the token.
	 * <p>
	 * A collection rather than one value so that somebody responsible for several
	 * libraries - the administrator of a shared system acting for its tenants - can be
	 * given exactly those libraries without being made a consortium administrator to
	 * do it.
	 */
	public static Collection<String> visibleAgencyCodes(DataFetchingEnvironment env) {
		final Collection<String> claimed = env.getGraphQlContext().get(AGENCY_CODES);

		return claimed != null ? claimed : List.of();
	}

	private static Collection<String> rolesOf(DataFetchingEnvironment env) {
		final Collection<String> roles = env.getGraphQlContext().get("roles");

		return roles != null ? roles : List.of();
	}
}
