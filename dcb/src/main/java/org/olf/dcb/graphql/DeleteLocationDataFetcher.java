package org.olf.dcb.graphql;

import java.util.*;
import java.util.concurrent.CompletableFuture;

import io.micronaut.http.HttpStatus;
import io.micronaut.http.exceptions.HttpStatusException;
import org.olf.dcb.storage.LocationRepository;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import io.micronaut.data.r2dbc.operations.R2dbcOperations;
import jakarta.inject.Singleton;
import reactor.core.publisher.Mono;

@Singleton
public class DeleteLocationDataFetcher implements DataFetcher<CompletableFuture<Map<String, Object>>> {

	private static Logger log = LoggerFactory.getLogger(DeleteLibraryDataFetcher.class);
	private LocationRepository locationRepository;
	private R2dbcOperations r2dbcOperations;

	public DeleteLocationDataFetcher(LocationRepository locationRepository,
																	 R2dbcOperations r2dbcOperations) {
		this.locationRepository = locationRepository;
		this.r2dbcOperations = r2dbcOperations;
	}

	@Override
	public CompletableFuture<Map<String, Object>> get(DataFetchingEnvironment env) {
		Map<String, Object> input_map = env.getArgument("input");
		String id = input_map.containsKey("id") ? input_map.get("id").toString() : null;
//		String entity = input_map.containsKey("entity") ? input_map.get("entity").toString() : "Not provided"; // Corresponds to coreType in DCB Admin.
		String reason = input_map.containsKey("reason") ? input_map.get("reason").toString() : null;
		String changeCategory = input_map.containsKey("changeCategory") ? input_map.get("changeCategory").toString() : null;
		String changeReferenceUrl = input_map.containsKey("changeReferenceUrl") ? input_map.get("changeReferenceUrl").toString() : null;
		String userString = Optional.ofNullable(env.getGraphQlContext().get("currentUser"))
			.map(Object::toString)
			.orElse("User not detected");

		log.debug("deleteLocationDataFetcher id: {}", id);

		if (id == null) {
			return CompletableFuture.completedFuture(createResult(false, "Location ID must be provided"));
		}

		Collection<String> roles = env.getGraphQlContext().get("roles");

		// Check if the user has the required role
		if (roles == null || (!roles.contains("ADMIN") && !roles.contains("CONSORTIUM_ADMIN"))) {
			log.warn("deleteLocationDataFetcher: Access denied for user {}: user does not have the required role to delete a location.", userString);
			throw new HttpStatusException(HttpStatus.UNAUTHORIZED, "Access denied: you do not have the required role to perform this action.");		}

		UUID entityId = UUID.fromString(id);

		return Mono.from(r2dbcOperations.withTransaction(status ->
				Mono.from(locationRepository.findById(entityId))
					.flatMap(location -> {
						if (location == null) {
							return Mono.error(new IllegalArgumentException("Location not found"));
						}
						location.setReason(reason);
						location.setLastEditedBy(userString);
						location.setChangeCategory(changeCategory);
						location.setChangeReferenceUrl(changeReferenceUrl);
						return Mono.from(locationRepository.update(location));
					})
					.then(Mono.from(locationRepository.delete(entityId)))
					.thenReturn(true)
			))
			.map(success -> createResult(success, success ? "Location deleted successfully" : "Failed to delete location"))
			.onErrorResume(e -> {
				log.error("Error deleting location", e);
				return Mono.just(createResult(false, "Error deleting location: " + e.getMessage()));
			})
			.toFuture();
	}
	// Could this just be a boolean?
	private Map<String, Object> createResult(boolean success, String message) {
		Map<String, Object> result = new HashMap<>();
		result.put("success", success);
		result.put("message", message);
		return result;
	}
}


