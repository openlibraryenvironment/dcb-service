package org.olf.dcb.graphql;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.Optional;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;

import io.micronaut.http.HttpStatus;
import io.micronaut.http.exceptions.HttpStatusException;
import org.olf.dcb.storage.LocationRepository;
import org.olf.dcb.storage.LibraryRepository;
import org.olf.dcb.storage.PersonRepository;
import org.olf.dcb.storage.LibraryGroupMemberRepository;
import org.olf.dcb.storage.LibraryContactRepository;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import io.micronaut.data.r2dbc.operations.R2dbcOperations;
import jakarta.inject.Singleton;
import reactor.core.publisher.Mono;

@Singleton
public class DeleteLibraryDataFetcher implements DataFetcher<CompletableFuture<Map<String, Object>>> {

	private static Logger log = LoggerFactory.getLogger(DeleteLibraryDataFetcher.class);

	private LibraryRepository libraryRepository;
	private LibraryContactRepository libraryContactRepository;

	private LibraryGroupMemberRepository libraryGroupMemberRepository;
	private R2dbcOperations r2dbcOperations;

	public DeleteLibraryDataFetcher(LibraryRepository libraryRepository,
																	PersonRepository personRepository,
																	LibraryContactRepository libraryContactRepository, LibraryGroupMemberRepository libraryGroupMemberRepository, LocationRepository locationRepository,
																	R2dbcOperations r2dbcOperations) {
		this.libraryRepository = libraryRepository;
		this.libraryContactRepository = libraryContactRepository;
		this.libraryGroupMemberRepository = libraryGroupMemberRepository;
		this.r2dbcOperations = r2dbcOperations;
	}


	@Override
	public CompletableFuture<Map<String, Object>> get(DataFetchingEnvironment env) {
		Map<String, Object> input_map = env.getArgument("input");
		String id = input_map.containsKey("id") ? input_map.get("id").toString() : null;
		String reason = input_map.containsKey("reason") ? input_map.get("reason").toString() : null;
		String changeCategory = input_map.containsKey("changeCategory") ? input_map.get("changeCategory").toString() : null;
		String changeReferenceUrl = input_map.containsKey("changeReferenceUrl") ? input_map.get("changeReferenceUrl").toString() : null;
		String userString = Optional.ofNullable(env.getGraphQlContext().get("userName"))
			.map(Object::toString)
			.orElse("User not detected");

		log.debug("deleteLibraryDataFetcher id: {}", id);

		if (id == null) {
			return CompletableFuture.completedFuture(createResult(false, "Library ID must be provided"));
		}

		UUID entityId = UUID.fromString(id);

		Collection<String> roles = env.getGraphQlContext().get("roles");

		// Check if the user has the required role
		if (roles == null || (!roles.contains("ADMIN") && !roles.contains("CONSORTIUM_ADMIN"))) {
			log.warn("deleteLibraryDataFetcher: Access denied for user {}: user does not have the required role to delete a library.", userString);
			throw new HttpStatusException(HttpStatus.UNAUTHORIZED, "Access denied: you do not have the required role to perform this action.");
		}

		return Mono.from(r2dbcOperations.withTransaction(status ->
				Mono.from(libraryRepository.findById(entityId))
					.flatMap(library -> {
						if (library == null) {
							return Mono.error(new IllegalArgumentException("Library not found"));
						}
						library.setReason(reason);
						library.setLastEditedBy(userString);
						library.setChangeCategory(changeCategory);
						library.setChangeReferenceUrl(changeReferenceUrl);
						return Mono.from(libraryRepository.update(library));
					})
					.then(Mono.when(
						Mono.from(libraryContactRepository.deleteAllByLibraryId(entityId)),
						Mono.from(libraryGroupMemberRepository.deleteByLibraryId(entityId)),
						Mono.from(libraryRepository.delete(entityId))
					))
					.thenReturn(true)
			))
			.map(success -> createResult(success, success ? "Library deleted successfully" : "Failed to delete library"))
			.onErrorResume(e -> {
				log.error("Error deleting library", e);
				return Mono.just(createResult(false, "Error deleting library: " + e.getMessage()));
			})
			.toFuture();
	}

	private Map<String, Object> createResult(boolean success, String message) {
		Map<String, Object> result = new HashMap<>();
		result.put("success", success);
		result.put("message", message);
		return result;
	}
}
