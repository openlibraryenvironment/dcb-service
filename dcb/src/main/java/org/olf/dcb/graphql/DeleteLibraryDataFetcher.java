package org.olf.dcb.graphql;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.olf.dcb.storage.LibraryGroupMemberRepository;
import org.olf.dcb.storage.LibraryRepository;
import org.olf.dcb.storage.LibraryContactRepository;
import org.olf.dcb.storage.PersonRepository;

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
	private PersonRepository personRepository;
	private LibraryContactRepository libraryContactRepository;

	private LibraryGroupMemberRepository libraryGroupMemberRepository;
	private R2dbcOperations r2dbcOperations;

	public DeleteLibraryDataFetcher(LibraryRepository libraryRepository,
																	PersonRepository personRepository,
																	LibraryContactRepository libraryContactRepository, LibraryGroupMemberRepository libraryGroupMemberRepository,
																	R2dbcOperations r2dbcOperations) {
		this.libraryRepository = libraryRepository;
		this.personRepository = personRepository;
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
		String userString = Optional.ofNullable(env.getGraphQlContext().get("currentUser"))
			.map(Object::toString)
			.orElse("User not detected");

		log.debug("deleteLibraryDataFetcher id: {}", id);

		if (id == null) {
			return CompletableFuture.completedFuture(createResult(false, "Library ID must be provided"));
		}

		UUID libraryId = UUID.fromString(id);

		return Mono.from(r2dbcOperations.withTransaction(status ->
				Mono.from(libraryRepository.findById(libraryId))
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
						Mono.from(libraryContactRepository.deleteAllByLibraryId(libraryId)),
						Mono.from(libraryGroupMemberRepository.deleteByLibraryId(libraryId)),
						Mono.from(libraryRepository.delete(libraryId))
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
