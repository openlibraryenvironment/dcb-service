package org.olf.dcb.graphql;

import java.util.Collection;
import java.util.UUID;

import org.olf.dcb.core.model.Library;
import org.olf.dcb.core.model.ScopeLevel;
import org.olf.dcb.storage.LibraryRepository;
import org.olf.dcb.storage.ConsortiumRepository;
import org.olf.dcb.storage.LibraryGroupRepository;

import graphql.schema.DataFetchingEnvironment;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.exceptions.HttpStatusException;
import jakarta.inject.Singleton;
import reactor.core.publisher.Mono;

/**
 * Who may publish an announcement, and at which scope. Consortium and group notices need
 * consortium-level authority; a library administrator may publish only for a library their
 * token names. Anything unreadable is refused.
 */
@Singleton
public class AnnouncementAuthorisation {

	private final LibraryRepository libraryRepository;
	private final LibraryGroupRepository libraryGroupRepository;
	private final ConsortiumRepository consortiumRepository;

	public AnnouncementAuthorisation(LibraryRepository libraryRepository,
		LibraryGroupRepository libraryGroupRepository, ConsortiumRepository consortiumRepository) {

		this.libraryRepository = libraryRepository;
		this.libraryGroupRepository = libraryGroupRepository;
		this.consortiumRepository = consortiumRepository;
	}

	/**
	 * Refuses unless this caller may write at this scope.
	 *
	 * Returns the scope id so it composes into a reactive chain rather than being an
	 * imperative check somebody can forget to look at.
	 */
	public Mono<UUID> assertMayWrite(DataFetchingEnvironment env, ScopeLevel scopeType,
		UUID scopeId) {

		final Collection<String> roles = env.getGraphQlContext().get("roles");

		if (roles == null) {
			return Mono.error(forbidden("no roles on this request"));
		}

		final var consortiumLevel = roles.contains("ADMIN") || roles.contains("CONSORTIUM_ADMIN");

		if (consortiumLevel) {
			return scopeExists(scopeType, scopeId);
		}

		if (!roles.contains("LIBRARY_ADMIN")) {
			return Mono.error(forbidden(
				"publishing an announcement requires ADMIN, CONSORTIUM_ADMIN or LIBRARY_ADMIN"));
		}

		if (scopeType != ScopeLevel.LIBRARY) {
			// The blast-radius rule. A consortium notice reaches every patron of every member
			// library, and a library administrator's authority stops at their own.
			return Mono.error(forbidden(
				"a library administrator may publish at LIBRARY scope only; " + scopeType
					+ " scope requires CONSORTIUM_ADMIN"));
		}

		return theirOwnLibrary(env, scopeId);
	}

	private Mono<UUID> theirOwnLibrary(DataFetchingEnvironment env, UUID scopeId) {
		final var visible = AgencyAccessScope.visibleAgencyCodes(env);

		if (visible == null || visible.isEmpty()) {
			return Mono.error(forbidden(
				"this account administers no library, so it may not publish for one"));
		}

		return Mono.from(libraryRepository.findById(scopeId))
			.map(Library::getAgencyCode)
			.filter(visible::contains)
			.map(code -> scopeId)
			.switchIfEmpty(Mono.error(forbidden(
				"this account does not administer the library this announcement is scoped to")));
	}

	// A notice at a scope that names nothing would be stored and shown to nobody
	private Mono<UUID> scopeExists(ScopeLevel scopeType, UUID scopeId) {
		final var exists = switch (scopeType) {
			case LIBRARY -> libraryRepository.existsById(scopeId);
			case LIBRARY_GROUP -> libraryGroupRepository.existsById(scopeId);
			case CONSORTIUM -> consortiumRepository.existsById(scopeId);
		};

		return Mono.from(exists)
			.filter(Boolean::booleanValue)
			.map(found -> scopeId)
			.switchIfEmpty(Mono.error(new HttpStatusException(HttpStatus.NOT_FOUND,
				"No " + scopeType + " with id " + scopeId)));
	}

	private static HttpStatusException forbidden(String because) {
		return new HttpStatusException(HttpStatus.FORBIDDEN, "Access denied: " + because + ".");
	}
}
