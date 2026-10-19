package org.olf.dcb.graphql;

import static org.olf.dcb.graphql.GraphQLSecurityContextCustomizer.AGENCY_CODES;
import static services.k_int.data.querying.lucene.LuceneFieldQueryNodeBuilder.QUERY_PATHS;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.PatronRequestAudit;
import org.olf.dcb.security.RequestInvolvement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import graphql.schema.DataFetchingEnvironment;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.repository.jpa.criteria.QuerySpecification;
import services.k_int.data.querying.QueryPath;

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
	 */
	private static final Set<String> UNRESTRICTED_ROLES = Set.of("ADMIN", "CONSORTIUM_ADMIN");

	private AgencyAccessScope() {
	}

	/**
	 * How a resource is attributed to libraries, for ownership that is more than a list of
	 * paths: the rows any of these agencies may see.
	 */
	@FunctionalInterface
	public interface Ownership<T> {
		QuerySpecification<T> visibleTo(Collection<String> agencyCodes);
	}

	/** Every library party to the request - see {@link RequestInvolvement}. */
	public static final Ownership<PatronRequest> PATRON_REQUEST_OWNERSHIP = RequestInvolvement::involving;

	/**
	 * A patron identity belongs to the library the patron was resolved to.
	 * <p>
	 * Identities with no resolved agency are permitted. Only home identities carry one
	 * - PatronService says so explicitly - so the virtual patrons DCB creates at
	 * supplying and pickup libraries have none, and a rule reading the agency alone
	 * would hide the pickup patron from the borrowing library that displays it on its
	 * own request. What is left visible is a DCB-generated record that belongs to no
	 * library; what is now hidden is another library's real patron.
	 * <p>
	 */
	public static final List<QueryPath> PATRON_IDENTITY_OWNERSHIP = List.of(
		QueryPath.joining("resolvedAgency").matching("code"));

	/**
	 * A supplier request belongs to the library supplying it, and to the library whose
	 * patron asked for it.
	 * <p>
	 * The supplying side is {@code localAgency} on the row itself rather than the
	 * borrowing side's path pushed down through the parent request. The looser form -
	 * "any supplier request of a request I supplied any part of" - would let a library
	 * that supplied an earlier, failed attempt read the row belonging to the library
	 * that supplied the successful one. Those are different libraries and only one of
	 * them is party to this row.
	 */
	public static final List<QueryPath> SUPPLIER_REQUEST_OWNERSHIP = List.of(
		QueryPath.property("localAgency"),
		QUERY_PATHS.get("patronAgencyCode").under("patronRequest"));

	/**
	 * An audit entry is visible to exactly the libraries that can see the request it audits:
	 * the same rule through the association, not a restatement of it.
	 */
	public static final Ownership<PatronRequestAudit> PATRON_REQUEST_AUDIT_OWNERSHIP =
		agencyCodes -> RequestInvolvement.involvingRequestVia("patronRequest", agencyCodes);

	// Locations are deliberately not scoped. They are directory data and are shared them across
	// libraries on purpose, including in discovery apps and DCB Admin for Libraries staff requesting.
	// PUA also makes it actively harmful to restrict who can view pickup locations.

	/**
	 * Narrow a query to the agencies this caller may see.
	 * <p>
	 * Applied to every query on the resource, not only the ones a list screen sends.
	 * A grid that filters itself is a convention; a detail route asking for one record
	 * by id is the same query with a different filter, and before this it returned
	 * whatever id it was given. Scoping the fetcher rather than the caller means both
	 * are covered by the same rule and neither can be the one that forgets.
	 *
	 * @param specification the filter the client asked for, or null for "everything"
	 * @param ownership how this resource is attributed to a library; any one matching
	 * is enough
	 * @return the specification to actually run
	 */
	public static <T> QuerySpecification<T> restrict(DataFetchingEnvironment env,
		QuerySpecification<T> specification, List<QueryPath> ownership) {

		return scope(env, specification, anyOf(ownership, false));
	}

	/** As {@link #restrict(DataFetchingEnvironment, QuerySpecification, List)}, for ownership that is not a path list. */
	public static <T> QuerySpecification<T> restrict(DataFetchingEnvironment env,
		QuerySpecification<T> specification, Ownership<T> ownership) {

		return scope(env, specification, ownership);
	}

	/**
	 * As {@link #restrict}, but also permits rows whose ownership path resolves to
	 * nothing.
	 * <p>
	 * Only for resources where an unattributed row belongs to nobody rather than to
	 * somebody unrecorded - the virtual patrons DCB creates for itself, which carry no
	 * agency by design.
	 */
	public static <T> QuerySpecification<T> restrictAllowingUnattributed(
		DataFetchingEnvironment env, QuerySpecification<T> specification,
		List<QueryPath> ownership) {

		return scope(env, specification, anyOf(ownership, true));
	}

	private static <T> Ownership<T> anyOf(List<QueryPath> ownership, boolean allowUnattributed) {
		if (ownership.isEmpty()) {
			throw new IllegalArgumentException("A resource with no ownership paths cannot be scoped");
		}

		return visible -> ownership.stream()
			.<QuerySpecification<T>>map(path -> allowUnattributed
				? path.isAnyOfOrAbsent(visible)
				: path.isAnyOf(visible))
			.reduce(QuerySpecification::or)
			.orElseThrow();
	}

	private static <T> QuerySpecification<T> scope(DataFetchingEnvironment env,
		QuerySpecification<T> specification, Ownership<T> ownership) {

		if (isUnrestricted(env)) {
			return specification;
		}

		final var visible = visibleAgencyCodes(env);

		if (visible.isEmpty()) {
			// Not a consortium role and no agency claim. Returning everything here would
			// make the whole control decorative, so this is a deliberate closed failure -
			// it should be read as "this token does not say who you are".
			log.warn("Query from a caller with neither a consortium role nor an agency "
				+ "claim; returning nothing");
		}

		final var permitted = ownership.visibleTo(visible);

		return specification == null ? permitted : specification.and(permitted);
	}

	public static boolean isUnrestricted(DataFetchingEnvironment env) {
		return rolesOf(env).stream().anyMatch(UNRESTRICTED_ROLES::contains);
	}

	/** Whether this caller may act on one agency's own records. */
	public static boolean permitsAgency(DataFetchingEnvironment env, @Nullable String agencyCode) {
		return isUnrestricted(env)
			|| (agencyCode != null && visibleAgencyCodes(env).contains(agencyCode));
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

	/**
	 * Context key under which the Host LMS records this caller administers are
	 * memoised. Resolving them means a query per claimed agency, and the answer is
	 * asked for once per Host LMS in a response - a library list of a hundred would
	 * otherwise repeat it a hundred times.
	 */
	public static final String PERMITTED_HOST_LMS_IDS = "permittedHostLmsIds";
}
