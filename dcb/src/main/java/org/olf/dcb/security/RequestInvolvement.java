package org.olf.dcb.security;

import static services.k_int.data.querying.lucene.LuceneFieldQueryNodeBuilder.QUERY_PATHS;

import java.util.Collection;
import java.util.UUID;

import org.olf.dcb.core.model.Location;
import org.olf.dcb.core.model.PatronRequest;

import io.micronaut.data.repository.jpa.criteria.QuerySpecification;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

/**
 * The libraries party to a patron request: the borrower, the current supplier and the pickup
 * library. An earlier supplier, whose attempt did not go ahead, is not: it never handled the item.
 * One rule for reading a request, writing notes and tags on it, and watching it, so a library can
 * never write to a request it cannot open.
 * <p>
 * Acting on the request's workflow is narrower, and is {@link PatronRequestAccessGuard}'s.
 * <p>
 * {@code PatronRequestRepository.findInvolvedAgencyCodes} is the same rule for one request, as SQL;
 * {@code RequestInvolvementTests} holds the two to each other.
 */
public final class RequestInvolvement {
	private RequestInvolvement() {
	}

	/** The requests any of these agencies is involved in. No agencies matches nothing. */
	public static QuerySpecification<PatronRequest> involving(Collection<String> agencyCodes) {
		final QuerySpecification<PatronRequest> borrower = QUERY_PATHS.get("patronAgencyCode").isAnyOf(agencyCodes);
		final QuerySpecification<PatronRequest> supplier = QUERY_PATHS.get("supplyingAgencyCode").isAnyOf(agencyCodes);

		return (root, query, criteriaBuilder) -> agencyCodes.isEmpty()
			? criteriaBuilder.disjunction()
			: criteriaBuilder.or(
				borrower.toPredicate(root, query, criteriaBuilder),
				supplier.toPredicate(root, query, criteriaBuilder),
				pickup(root, query, criteriaBuilder, agencyCodes));
	}

	/**
	 * The rows of an entity that belongs to a patron request, through {@code association}, whose
	 * request any of these agencies is involved in.
	 */
	public static <T> QuerySpecification<T> involvingRequestVia(String association,
		Collection<String> agencyCodes) {

		final var involved = involving(agencyCodes);

		return (root, query, criteriaBuilder) -> {
			final Subquery<UUID> requests = query.subquery(UUID.class);
			final Root<PatronRequest> request = requests.from(PatronRequest.class);
			requests.select(request.get("id"))
				.where(involved.toPredicate(request, query, criteriaBuilder));

			return root.join(association, JoinType.INNER).get("id").in(requests);
		};
	}

	// pickup_location_code holds the location's UUID as text, so the location's id is compared as text
	private static Predicate pickup(Root<PatronRequest> root, CriteriaQuery<?> query,
		CriteriaBuilder criteriaBuilder, Collection<String> agencyCodes) {

		final Subquery<String> locations = query.subquery(String.class);
		final Root<Location> location = locations.from(Location.class);
		locations.select(location.get("id").cast(String.class))
			.where(location.join("agency", JoinType.INNER).get("code").in(agencyCodes));

		return root.get("pickupLocationCode").in(locations);
	}
}
