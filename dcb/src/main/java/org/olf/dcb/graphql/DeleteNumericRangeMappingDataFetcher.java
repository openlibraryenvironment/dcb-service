package org.olf.dcb.graphql;

import java.util.UUID;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Collection;



import java.util.concurrent.CompletableFuture;

import io.micronaut.http.HttpStatus;
import io.micronaut.http.exceptions.HttpStatusException;
import org.olf.dcb.storage.NumericRangeMappingRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import io.micronaut.data.r2dbc.operations.R2dbcOperations;
import jakarta.inject.Singleton;
import reactor.core.publisher.Mono;

@Singleton
public class DeleteNumericRangeMappingDataFetcher implements DataFetcher<CompletableFuture<Map<String, Object>>> {

	private static Logger log = LoggerFactory.getLogger(DeleteNumericRangeMappingDataFetcher.class);
	private NumericRangeMappingRepository numericRangeMappingRepository;
	private R2dbcOperations r2dbcOperations;

	public DeleteNumericRangeMappingDataFetcher(NumericRangeMappingRepository numericRangeMappingRepository,
																								R2dbcOperations r2dbcOperations) {
		this.numericRangeMappingRepository = numericRangeMappingRepository;
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

		log.debug("deleteNumericRangeMappingDataFetcher id: {}", id);

		if (id == null) {
			return CompletableFuture.completedFuture(createResult(false, "NumericRangeMapping ID must be provided"));
		}

		Collection<String> roles = env.getGraphQlContext().get("roles");

		// Check if the user has the required role
		if (roles == null || (!roles.contains("ADMIN") && !roles.contains("CONSORTIUM_ADMIN"))) {
			log.warn("deleteNumericRangeMappingDataFetcher: Access denied for user {}: user does not have the required role to delete a numericRangeMapping.", userString);
			throw new HttpStatusException(HttpStatus.UNAUTHORIZED, "Access denied: you do not have the required role to perform this action.");
		}

		UUID entityId = UUID.fromString(id);


		return Mono.from(r2dbcOperations.withTransaction(status ->
				Mono.from(numericRangeMappingRepository.findById(entityId))
					.flatMap(numericRangeMapping -> {
						if (numericRangeMapping == null) {
							return Mono.error(new IllegalArgumentException("Numeric range mapping not found"));
						}
						numericRangeMapping.setReason(reason);
						numericRangeMapping.setLastEditedBy(userString);
						numericRangeMapping.setChangeCategory(changeCategory);
						numericRangeMapping.setChangeReferenceUrl(changeReferenceUrl);
						return Mono.from(numericRangeMappingRepository.update(numericRangeMapping));
					})
					.then(Mono.when(
						Mono.from(numericRangeMappingRepository.delete(entityId))
					))
					.thenReturn(true)
			))
			.map(success -> createResult(success, success ? "Numeric range mapping deleted successfully" : "Failed to delete numeric range mapping"))
			.onErrorResume(e -> {
				log.error("Error deleting numericRangeMapping", e);
				return Mono.just(createResult(false, "Error deleting numeric range mapping: " + e.getMessage()));
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
