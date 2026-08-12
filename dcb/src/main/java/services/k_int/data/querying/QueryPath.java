package services.k_int.data.querying;

import java.util.Collection;
import java.util.List;

import io.micronaut.data.repository.jpa.criteria.QuerySpecification;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Path;

/**
 * A queryable field name that is not a property of the entity being queried.
 * <p>
 * Most filters map straight onto a column. A few have to reach through an
 * association first - "which agency is this request's patron from" lives on the
 * requesting identity, not on the request - and those were each written as a bespoke
 * method plus a branch in an if-chain. Three of them was enough to make the shape
 * obvious: join some associations, then compare a property on the far end. Stating
 * that as data means adding the next one is a line in a table rather than a method
 * and a branch, and it means the join handling below is written once instead of
 * three times.
 * <p>
 * Deliberately not a general expression language. Anything that does not fit "join,
 * then compare" should be written as its own {@link QuerySpecification} rather than
 * bent into this.
 */
public record QueryPath(List<String> joins, String property, MatchMode matchMode) {

	public enum MatchMode {
		/** Exact match, for codes and identifiers. */
		EQUALS,
		/** Substring match, for values that are not stored cleanly enough to compare whole. */
		CONTAINS
	}

	/**
	 * Start from the associations to traverse, in order.
	 * <p>
	 * Reads as the path it describes:
	 * {@code joining("requestingIdentity", "resolvedAgency").matching("code")}.
	 */
	public static Builder joining(String... joins) {
		return new Builder(List.of(joins));
	}

	public record Builder(List<String> joins) {
		public QueryPath matching(String property) {
			return new QueryPath(joins, property, MatchMode.EQUALS);
		}

		public QueryPath containing(String property) {
			return new QueryPath(joins, property, MatchMode.CONTAINS);
		}
	}

	public <T> QuerySpecification<T> is(String value) {
		return (root, query, criteriaBuilder) -> {
			final Path<String> path = resolve(root);

			return matchMode == MatchMode.CONTAINS
				? criteriaBuilder.like(path, "%" + value + "%")
				: criteriaBuilder.equal(path, value);
		};
	}

	/**
	 * Matches any of the given values. An empty collection matches nothing, which is
	 * the answer a caller asking "restrict this to the following agencies, of which
	 * there are none" should get.
	 */
	public <T> QuerySpecification<T> isAnyOf(Collection<String> values) {
		return (root, query, criteriaBuilder) -> values.isEmpty()
			? criteriaBuilder.disjunction()
			: resolve(root).in(values);
	}

	private Path<String> resolve(From<?, ?> root) {
		From<?, ?> from = root;

		for (String join : joins) {
			from = joinOnce(from, join);
		}

		return from.get(property);
	}

	/**
	 * Reuse a join this query already has rather than adding a second one.
	 * <p>
	 * Two specifications naming the same association used to each add their own join.
	 * Against a to-many association that multiplies rows - a request with two supplier
	 * requests comes back twice - and it quietly changes what an AND means: separate
	 * joins ask "some supplier request matches A and some supplier request matches B",
	 * a shared join asks "one supplier request matches both". The second reading is the
	 * one callers expect, and it is the one that makes an access-scope predicate
	 * actually restrict a filter the caller supplied rather than sitting beside it.
	 */
	private static From<?, ?> joinOnce(From<?, ?> from, String association) {
		// An explicit loop rather than a stream: getJoins() is generic over a captured
		// type, and every way of expressing "find the first, else create one" through
		// Optional needs a cast the compiler will not accept.
		for (Join<?, ?> existing : from.getJoins()) {
			if (association.equals(existing.getAttribute().getName())
				&& existing.getJoinType() == JoinType.LEFT) {

				return existing;
			}
		}

		return from.join(association, JoinType.LEFT);
	}
}
