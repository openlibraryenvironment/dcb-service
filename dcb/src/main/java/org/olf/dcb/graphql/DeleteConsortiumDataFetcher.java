package org.olf.dcb.graphql;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.Optional;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;

import io.micronaut.http.HttpStatus;
import io.micronaut.http.exceptions.HttpStatusException;
import org.olf.dcb.storage.ConsortiumContactRepository;
import org.olf.dcb.storage.ConsortiumFunctionalSettingRepository;
import org.olf.dcb.storage.ConsortiumRepository;


import org.olf.dcb.storage.LibraryGroupRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import io.micronaut.data.r2dbc.operations.R2dbcOperations;
import jakarta.inject.Singleton;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Singleton
public class DeleteConsortiumDataFetcher implements DataFetcher<CompletableFuture<Map<String, Object>>> {

	private static Logger log = LoggerFactory.getLogger(DeleteConsortiumDataFetcher.class);

	private ConsortiumRepository consortiumRepository;
	private ConsortiumContactRepository consortiumContactRepository;
	private ConsortiumFunctionalSettingRepository consortiumFunctionalSettingRepository;

	private LibraryGroupRepository libraryGroupRepository;

	private R2dbcOperations r2dbcOperations;

	public DeleteConsortiumDataFetcher(ConsortiumRepository consortiumRepository, ConsortiumContactRepository consortiumContactRepository, ConsortiumFunctionalSettingRepository consortiumFunctionalSettingRepository, LibraryGroupRepository libraryGroupRepository,
																		 R2dbcOperations r2dbcOperations) {
		this.consortiumRepository = consortiumRepository;
		this.consortiumContactRepository = consortiumContactRepository;
		this.consortiumFunctionalSettingRepository = consortiumFunctionalSettingRepository;
		this.libraryGroupRepository = libraryGroupRepository;
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

		log.debug("deleteConsortiumDataFetcher id: {}", id);

		if (id == null) {
			return CompletableFuture.completedFuture(createResult(false, "Consortium ID must be provided"));
		}

		UUID entityId = UUID.fromString(id);

		Collection<String> roles = env.getGraphQlContext().get("roles");

		// Check if the user has the required role
		if (roles == null || (!roles.contains("ADMIN"))) {
			log.warn("deleteConsortiumDataFetcher: Access denied for user {}: user does not have the required role to delete a consortium.", userString);
			throw new HttpStatusException(HttpStatus.UNAUTHORIZED, "Access denied: you do not have the required role to perform this action.");
		}

		return Mono.from(consortiumRepository.findById(entityId))
			.flatMap(consortium -> {
				if (consortium == null) {
					log.error("Consortium not found for ID: {}", entityId);
					return Mono.just(createResult(false, "Consortium not found"));
				}

				UUID libraryGroupId = consortium.getLibraryGroup() != null ?
					consortium.getLibraryGroup().getId() : null;

				log.debug("Found consortium with id: {}, libraryGroupId: {}", entityId, libraryGroupId);

				if (libraryGroupId == null) {
					// If no library group, just delete the single consortium + associated info
					return Mono.from(deleteConsortium(entityId,
							input_map.containsKey("reason") ? input_map.get("reason").toString() : null,
							input_map.containsKey("changeCategory") ? input_map.get("changeCategory").toString() : null,
							input_map.containsKey("changeReferenceUrl") ? input_map.get("changeReferenceUrl").toString() : null,
							userString))
						.thenReturn(createResult(true, "The consortium and its associated data were deleted successfully"));
				}

				// Handles the deletion of consortia that have library groups associated with them.
				// Also handles legacy case of consortia sharing a library group
				return Flux.from(consortiumRepository.findByLibraryGroupId(libraryGroupId))
					.collectList()
					.flatMap(consortia -> {
						boolean multipleConsortia = consortia.size() > 1;
						if (multipleConsortia) {
								log.debug("Found {} legacy consortia sharing library group {}. These consortia will be deleted.", consortia.size(), libraryGroupId);
						} else {
								log.debug("Found one consortium {} with an associated library group {}. This consortium will be deleted", consortia, libraryGroupId);
						}
						return Flux.fromIterable(consortia)
							.flatMap(c -> deleteConsortium(
								c.getId(),
								input_map.containsKey("reason") ? input_map.get("reason").toString() : null,
								input_map.containsKey("changeCategory") ? input_map.get("changeCategory").toString() : null,
								input_map.containsKey("changeReferenceUrl") ? input_map.get("changeReferenceUrl").toString() : null,
								userString))
							.then(Mono.from(libraryGroupRepository.delete(libraryGroupId)))
							.thenReturn(createResult(true, multipleConsortia ? "The legacy consortia and their shared library group were deleted successfully" : "The consortium, its associated data and its associated library group were deleted successfully"));
					});
			})
			.onErrorResume(e -> {
				log.error("Error during consortium deletion", e);
				return Mono.just(createResult(false, "Error deleting consortium: " + e.getMessage()));
			})
			.defaultIfEmpty(createResult(false, "No consortium was found with the UUID provided"))
			.toFuture();
	}

	private Mono<Void> deleteConsortium(UUID consortiumId, String reason, String changeCategory,
																			String changeReferenceUrl, String userString) {
		log.debug("Deleting consortium {} and associated information. This will not delete a consortium's libraries.", consortiumId);
		return Mono.from(consortiumRepository.findById(consortiumId))
			.flatMap(consortium -> {
				// Update consortium with audit info and null out library group
				consortium.setReason(reason);
				consortium.setLastEditedBy(userString);
				consortium.setChangeCategory(changeCategory);
				consortium.setChangeReferenceUrl(changeReferenceUrl);
				consortium.setLibraryGroup(null);

				return Mono.from(consortiumRepository.update(consortium))
					.then(Mono.when(
						Mono.from(consortiumContactRepository.deleteAllByConsortiumId(consortiumId)),
						Mono.from(consortiumFunctionalSettingRepository.deleteAllByConsortiumId(consortiumId))
					))
					.then(Mono.from(consortiumRepository.delete(consortiumId)));
			});
	}

	private Map<String, Object> createResult(boolean success, String message) {
		Map<String, Object> result = new HashMap<>();
		result.put("success", success);
		result.put("message", message);
		return result;
	}
}
