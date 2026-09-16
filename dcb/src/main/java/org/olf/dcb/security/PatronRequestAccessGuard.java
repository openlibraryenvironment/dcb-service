package org.olf.dcb.security;

import static services.k_int.data.querying.lucene.LuceneFieldQueryNodeBuilder.QUERY_PATHS;

import java.util.List;
import java.util.UUID;

import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.storage.postgres.PostgresPatronRequestRepository;

import io.micronaut.data.model.Pageable;
import io.micronaut.data.repository.jpa.criteria.QuerySpecification;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.security.authentication.Authentication;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import services.k_int.data.querying.QueryPath;

/**
 * Whether a caller may act on one patron request through the staff REST API. Consortium-level
 * callers are never checked; library-level callers must belong to a library party to the request.
 */
@Slf4j
@Singleton
public class PatronRequestAccessGuard {
	/**
	 * A patron request belongs to the library that borrowed it and the library that supplied it.
	 * GraphQL scopes the patron request grids by this same list (AgencyAccessScope).
	 */
	public static final List<QueryPath> PATRON_REQUEST_OWNERSHIP = List.of(
		QUERY_PATHS.get("patronAgencyCode"),
		QUERY_PATHS.get("supplyingAgencyCode"));

	private final PostgresPatronRequestRepository patronRequestRepository;

	public PatronRequestAccessGuard(PostgresPatronRequestRepository patronRequestRepository) {
		this.patronRequestRepository = patronRequestRepository;
	}

	public Mono<UUID> requireOwnership(UUID patronRequestId, Authentication authentication) {
		final var caller = CallerScope.from(authentication.getRoles(), authentication.getAttributes());

		if (!caller.requiresNarrowing()) {
			return Mono.just(patronRequestId);
		}

		if (caller.isIncoherent()) {
			return refuse(patronRequestId, "library role with no agency claim");
		}

		final QuerySpecification<PatronRequest> byId =
			(root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("id"), patronRequestId);

		final QuerySpecification<PatronRequest> owned = PATRON_REQUEST_OWNERSHIP.stream()
			.<QuerySpecification<PatronRequest>>map(path -> path.isAnyOf(caller.agencyCodes()))
			.reduce(QuerySpecification::or)
			.orElseThrow();

		return Mono.from(patronRequestRepository.findAll(byId.and(owned), Pageable.from(0, 1)))
			.flatMap(page -> page.getContent().isEmpty()
				? refuse(patronRequestId, "not owned by " + caller.agencyCodes())
				: Mono.just(patronRequestId));
	}

	// 403 whether the request is someone else's or does not exist, so a library caller cannot probe ids.
	private static Mono<UUID> refuse(UUID patronRequestId, String reason) {
		log.warn("Refusing access to patron request {}: {}", patronRequestId, reason);

		return Mono.error(new HttpStatusException(HttpStatus.FORBIDDEN,
			"Access denied: this request does not belong to your library."));
	}
}
