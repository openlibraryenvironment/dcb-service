package org.olf.dcb.graphql;

import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import io.micronaut.data.r2dbc.operations.R2dbcOperations;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.exceptions.HttpStatusException;
import jakarta.inject.Singleton;
import org.olf.dcb.core.model.Location;
import org.olf.dcb.storage.LocationRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.Map;
import java.util.concurrent.CompletableFuture;


@Singleton
public class UpdateLocationDataFetcher implements DataFetcher<CompletableFuture<Location>> {

	private final LocationRepository locationRepository;

	private final R2dbcOperations r2dbcOperations;

	private static Logger log = LoggerFactory.getLogger(DataFetchers.class);


	public UpdateLocationDataFetcher(LocationRepository locationRepository, R2dbcOperations r2dbcOperations) {
		this.locationRepository = locationRepository;
		this.r2dbcOperations = r2dbcOperations;
	}

	@Override
	public CompletableFuture<Location> get(DataFetchingEnvironment env) {
		Map<String, Object> input_map = env.getArgument("input");
		log.debug("editLocationDataFetcher {}", input_map);

		UUID id = input_map.get("id") != null ? UUID.fromString(input_map.get("id").toString()) : null;

		String locationType = input_map.containsKey("type") ?
			(input_map.get("type").toString()) : null;
		Double latitude = input_map.containsKey("latitude") ?
                (Double) input_map.get("latitude") : null;
		Double longitude = input_map.containsKey("longitude") ?
			(Double) input_map.get("longitude") : null;
		Optional<String> name = Optional.ofNullable(input_map.get("name"))
			.map(Object::toString);
		Optional<String> reason = Optional.ofNullable(input_map.get("reason"))
			.map(Object::toString);
		Optional<String> changeReferenceUrl = Optional.ofNullable(input_map.get("changeReferenceUrl"))
			.map(Object::toString);
		Optional<String> changeCategory = Optional.ofNullable(input_map.get("changeCategory"))
			.map(Object::toString);
		String userString = Optional.ofNullable(env.getGraphQlContext().get("currentUser"))
			.map(Object::toString)
			.orElse("User not detected");
		Collection<String> roles = env.getGraphQlContext().get("roles");

		// Check if the user has the required role to edit location information
		if (roles == null || (!roles.contains("ADMIN") && !roles.contains("CONSORTIUM_ADMIN") && !roles.contains("LIBRARY_ADMIN"))) {
			log.warn("updateLibraryDataFetcher: Access denied for user {}: user does not have the required role to update a location.", userString);
			throw new HttpStatusException(HttpStatus.UNAUTHORIZED, "Access denied: you do not have the required role to perform this action.");		}

		Mono<Location> transactionMono = Mono.from(r2dbcOperations.withTransaction(status ->
			Mono.from(locationRepository.findById(id))
				.flatMap(location -> {
					if (locationType != null) {
						location.setType(locationType);
					}
					if (latitude != null) {
						location.setLatitude(latitude);
					}
					if (longitude != null) {
						location.setLongitude(longitude);
					}
					name.ifPresent(location::setName);
					location.setLastEditedBy(userString);
					changeReferenceUrl.ifPresent(location::setChangeReferenceUrl);
					changeCategory.ifPresent(location::setChangeCategory);
					reason.ifPresent(location::setReason);
					return Mono.from(locationRepository.update(location));
				})
		));

		return transactionMono.toFuture();
	}
}