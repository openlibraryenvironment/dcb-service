package org.olf.dcb.graphql;

import java.util.UUID;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Optional;
import java.util.Collection;



import java.util.concurrent.CompletableFuture;

import io.micronaut.http.HttpStatus;
import io.micronaut.http.exceptions.HttpStatusException;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.storage.LocationRepository;


import org.olf.dcb.storage.PatronRequestRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import io.micronaut.data.r2dbc.operations.R2dbcOperations;
import jakarta.inject.Singleton;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Singleton
public class DeleteLocationDataFetcher implements DataFetcher<CompletableFuture<Map<String, Object>>> {

	private static Logger log = LoggerFactory.getLogger(DeleteLibraryDataFetcher.class);
	private LocationRepository locationRepository;
	private PatronRequestRepository patronRequestRepository;
	private R2dbcOperations r2dbcOperations;

	public DeleteLocationDataFetcher(LocationRepository locationRepository, PatronRequestRepository patronRequestRepository,
																	 R2dbcOperations r2dbcOperations) {
		this.locationRepository = locationRepository;
		this.patronRequestRepository = patronRequestRepository;
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

		log.debug("deleteLocationDataFetcher id: {}", id);

		if (id == null) {
			return CompletableFuture.completedFuture(createResult(false, "Location ID must be provided"));
		}

		Collection<String> roles = env.getGraphQlContext().get("roles");

		// Check if the user has the required role
		if (roles == null || (!roles.contains("ADMIN") && !roles.contains("CONSORTIUM_ADMIN"))) {
			log.warn("deleteLocationDataFetcher: Access denied for user {}: user does not have the required role to delete a location.", userString);
			throw new HttpStatusException(HttpStatus.UNAUTHORIZED, "Access denied: you do not have the required role to perform this action.");
		}

		UUID entityId = UUID.fromString(id);

		return Mono.from(r2dbcOperations.withTransaction(status ->
				Mono.from(locationRepository.findById(entityId))
					.flatMap(location -> {
						if (location == null) {
							return Mono.error(new IllegalArgumentException("Location not found"));
						}

						// Check to see if any patron requests are associated with the location before deleting it.
						// Flux as there can be multiple
						return Flux.from(patronRequestRepository.findAllByPickupLocationCode(location.getId().toString()))
							.collectList()  // Collect all results into a List
							.flatMap(patronRequests -> {
								if (patronRequests != null && !patronRequests.isEmpty()) {
									log.debug("Patron request(s) have been found for this location.");
									// Filter requests where status is not COMPLETED and status is not null
									List<PatronRequest> nonCompletedRequests = patronRequests.stream()
										.filter(request -> request.getStatus() != null && !request.getStatus().equals(PatronRequest.Status.COMPLETED))
										.toList();
									if (!nonCompletedRequests.isEmpty()) {
										// If any non-COMPLETED requests exist, return an error listing them
										return Mono.error(new IllegalArgumentException("Patron requests exist for this location with non-COMPLETED status: " + nonCompletedRequests));
									}
									else
									{
										log.debug("The patron request(s) found for this location are in a COMPLETED state and so it is safe to delete them."+patronRequests);
									}
								}
								else
								{
									log.debug("There are no patron requests associated with this location and it is safe to delete.");
								}
								// Continue with updating the location
								location.setReason(reason);
								location.setLastEditedBy(userString);
								location.setChangeCategory(changeCategory);
								location.setChangeReferenceUrl(changeReferenceUrl);

								return Mono.from(locationRepository.update(location))
									.then(Mono.from(locationRepository.delete(entityId)))
									.thenReturn(true);
							});
					})
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


