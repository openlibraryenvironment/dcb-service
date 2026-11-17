package org.olf.dcb.request.notes;

import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.security.RequestInvolvement;
import org.olf.dcb.storage.PatronRequestRepository;
import org.olf.dcb.storage.postgres.PostgresPatronRequestRepository;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.repository.jpa.criteria.QuerySpecification;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.exceptions.HttpStatusException;
import jakarta.inject.Singleton;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Who may read and write notes and tags on a request: the libraries involved in it
 * (RequestInvolvement), and consortium staff.
 */
@Singleton
public class RequestNoteAccess {
	private final PatronRequestRepository patronRequestRepository;
	private final PostgresPatronRequestRepository patronRequestQueries;

	public RequestNoteAccess(PatronRequestRepository patronRequestRepository,
		PostgresPatronRequestRepository patronRequestQueries) {

		this.patronRequestRepository = patronRequestRepository;
		this.patronRequestQueries = patronRequestQueries;
	}

	public Mono<Set<String>> involvedAgencyCodes(UUID patronRequestId) {
		return Flux.from(patronRequestRepository.findInvolvedAgencyCodes(patronRequestId))
			.collect(TreeSet::new, Set::add);
	}

	public Mono<Boolean> canRead(UUID patronRequestId, RequestNoteCaller caller) {
		if (caller.consortium()) {
			return Mono.just(true);
		}

		return involvedAgencyCodes(patronRequestId)
			.map(involved -> caller.agencyCodes().stream().anyMatch(involved::contains));
	}

	/**
	 * Which of these requests the caller may read, by the same rule as {@link #canRead}: no query for
	 * consortium staff, and one for a library however many requests are asked about.
	 */
	public Mono<Set<UUID>> readableAmong(Collection<UUID> patronRequestIds, RequestNoteCaller caller) {
		if (caller.consortium()) {
			return Mono.just(Set.copyOf(patronRequestIds));
		}

		if (patronRequestIds.isEmpty()) {
			return Mono.just(Set.of());
		}

		final QuerySpecification<PatronRequest> among =
			(root, query, criteriaBuilder) -> root.get("id").in(patronRequestIds);

		return Flux.from(patronRequestQueries.findAll(among.and(RequestInvolvement.involving(caller.agencyCodes()))))
			.map(PatronRequest::getId)
			.collect(Collectors.toSet());
	}

	/**
	 * The caller's agencies that are involved in the request decide which library a library user
	 * writes as; the client may only choose among them.
	 */
	public Mono<RequestNoteAuthor> authorFor(UUID patronRequestId, RequestNoteCaller caller,
		@Nullable String requestedAgencyCode) {

		if (caller.consortium()) {
			return Mono.from(patronRequestRepository.findById(patronRequestId))
				.map(request -> RequestNoteAuthor.consortium())
				.switchIfEmpty(Mono.error(new HttpStatusException(HttpStatus.NOT_FOUND,
					"No such patron request.")));
		}

		return involvedAgencyCodes(patronRequestId)
			.flatMap(involved -> {
				final Set<String> candidates = new TreeSet<>(caller.agencyCodes());
				candidates.retainAll(involved);

				if (candidates.isEmpty()) {
					return Mono.error(notYourRequest());
				}

				if (requestedAgencyCode != null) {
					return candidates.contains(requestedAgencyCode)
						? Mono.just(RequestNoteAuthor.library(requestedAgencyCode))
						: Mono.error(notYourRequest());
				}

				if (candidates.size() > 1) {
					return Mono.error(new HttpStatusException(HttpStatus.BAD_REQUEST,
						"Name the library to write as: one of " + candidates + "."));
				}

				return Mono.just(RequestNoteAuthor.library(candidates.iterator().next()));
			});
	}

	// The same answer whether the request is someone else's or does not exist, so ids cannot be probed
	static HttpStatusException notYourRequest() {
		return new HttpStatusException(HttpStatus.FORBIDDEN,
			"Access denied: this request does not belong to your library.");
	}
}
