package org.olf.dcb.graphql;

import java.util.UUID;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Collection;



import java.util.concurrent.CompletableFuture;

import io.micronaut.http.HttpStatus;
import io.micronaut.http.exceptions.HttpStatusException;
import org.olf.dcb.storage.ReferenceValueMappingRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import io.micronaut.data.r2dbc.operations.R2dbcOperations;
import jakarta.inject.Singleton;
import reactor.core.publisher.Mono;

@Singleton
public class DeleteReferenceValueMappingDataFetcher implements DataFetcher<CompletableFuture<Map<String, Object>>> {

	private static Logger log = LoggerFactory.getLogger(DeleteReferenceValueMappingDataFetcher.class);
	private ReferenceValueMappingRepository referenceValueMappingRepository;
	private R2dbcOperations r2dbcOperations;

	public DeleteReferenceValueMappingDataFetcher(ReferenceValueMappingRepository referenceValueMappingRepository,
																	 R2dbcOperations r2dbcOperations) {
		this.referenceValueMappingRepository = referenceValueMappingRepository;
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

		log.debug("deleteReferenceValueMappingDataFetcher id: {}", id);

		if (id == null) {
			return CompletableFuture.completedFuture(createResult(false, "ReferenceValueMapping ID must be provided"));
		}

		Collection<String> roles = env.getGraphQlContext().get("roles");

		// Check if the user has the required role
		if (roles == null || (!roles.contains("ADMIN") && !roles.contains("CONSORTIUM_ADMIN"))) {
			log.warn("deleteReferenceValueMappingDataFetcher: Access denied for user {}: user does not have the required role to delete a referenceValueMapping.", userString);
			throw new HttpStatusException(HttpStatus.UNAUTHORIZED, "Access denied: you do not have the required role to perform this action.");
		}

		UUID entityId = UUID.fromString(id);


		return Mono.from(r2dbcOperations.withTransaction(status ->
				Mono.from(referenceValueMappingRepository.findById(entityId))
					.flatMap(referenceValueMapping -> {
						if (referenceValueMapping == null) {
							return Mono.error(new IllegalArgumentException("Reference value mapping not found"));
						}
						referenceValueMapping.setReason(reason);
						referenceValueMapping.setLastEditedBy(userString);
						referenceValueMapping.setChangeCategory(changeCategory);
						referenceValueMapping.setChangeReferenceUrl(changeReferenceUrl);
						return Mono.from(referenceValueMappingRepository.update(referenceValueMapping));
					})
					.then(Mono.when(
						Mono.from(referenceValueMappingRepository.delete(entityId))
					))
					.thenReturn(true)
			))
			.map(success -> createResult(success, success ? "Reference value mapping deleted successfully" : "Failed to delete reference value mapping"))
			.onErrorResume(e -> {
				log.error("Error deleting referenceValueMapping", e);
				return Mono.just(createResult(false, "Error deleting reference value mapping: " + e.getMessage()));
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


