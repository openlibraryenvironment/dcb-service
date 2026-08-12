package services.k_int.data.querying;

import java.util.Collection;
import java.util.List;

import io.micronaut.data.repository.jpa.criteria.QuerySpecification;
import jakarta.persistence.criteria.From;
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

	/**
	 * Matches any of the given values, and also rows where the path resolves to
	 * nothing.
	 * <p>
	 * For an access scope this says "or it belongs to nobody". Use it only where an
	 * unattributed row is genuinely not anybody's private data - a record DCB created
	 * for itself rather than one whose owner simply was not recorded.
	 */
	public <T> QuerySpecification<T> isAnyOfOrAbsent(Collection<String> values) {
		return (root, query, criteriaBuilder) -> {
			final var path = resolve(root);

			return values.isEmpty()
				? criteriaBuilder.isNull(path)
				: criteriaBuilder.or(path.in(values), criteriaBuilder.isNull(path));
		};
	}

	/**
	 * Each specification adds its own joins.
	 * <p>
	 * Reusing a join another specification already made would be preferable, but
	 * Micronaut Data's criteria implementation raises "Not supported operation!" from
	 * {@code Join.getAttribute()}, so there is no way to ask an existing join what it
	 * joined. Recognising it by anything else means casting to Micronaut's own path
	 * types, which is a heavier coupling than the problem deserves.
	 * <p>
	 * What that costs, against a to-many association only: two predicates on the same
	 * association read as "some row matches A and some row matches B" rather than "one
	 * row matches both", and a match on several rows can repeat the parent. Both were
	 * true of the hand-written specifications this replaced. For the access scopes
	 * built on it the looser reading is still sound - a request whose supplier is mine
	 * is mine to see, whatever else the caller filtered on.
	 */
	private Path<String> resolve(From<?, ?> root) {
		From<?, ?> from = root;

		for (String join : joins) {
			from = from.join(join, JoinType.LEFT);
		}

		return from.get(property);
	}
}
