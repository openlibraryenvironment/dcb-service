package org.olf.dcb.graphql;

import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import io.micronaut.data.r2dbc.operations.R2dbcOperations;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.exceptions.HttpStatusException;
import jakarta.inject.Singleton;
import org.olf.dcb.core.model.Consortium;
import org.olf.dcb.storage.ConsortiumRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.Map;
import java.util.concurrent.CompletableFuture;


@Singleton
public class UpdateConsortiumDataFetcher implements DataFetcher<CompletableFuture<Consortium>> {

	private final ConsortiumRepository consortiumRepository;

	private final R2dbcOperations r2dbcOperations;

	private static Logger log = LoggerFactory.getLogger(DataFetchers.class);


	public UpdateConsortiumDataFetcher(ConsortiumRepository consortiumRepository, R2dbcOperations r2dbcOperations) {
		this.consortiumRepository = consortiumRepository;
		this.r2dbcOperations = r2dbcOperations;
	}

	@Override
	public CompletableFuture<Consortium> get(DataFetchingEnvironment env) {
		Map<String, Object> input_map = env.getArgument("input");
		log.debug("updateConsortiumDataFetcher {}", input_map);

		UUID id = input_map.get("id") != null ? UUID.fromString(input_map.get("id").toString()) : null;

		String userString = Optional.ofNullable(env.getGraphQlContext().get("userName"))
			.map(Object::toString)
			.orElse("User not detected");
		String email = Optional.ofNullable(env.getGraphQlContext().get("userEmail"))
			.map(Object::toString)
			.orElse("User not detected");
		Optional<String> reason = Optional.ofNullable(input_map.get("reason"))
			.map(Object::toString);
		Optional<String> changeReferenceUrl = Optional.ofNullable(input_map.get("changeReferenceUrl"))
			.map(Object::toString);
		Optional<String> changeCategory = Optional.ofNullable(input_map.get("changeCategory"))
			.map(Object::toString);
		String displayName = input_map.containsKey("displayName") ?
			input_map.get("displayName").toString() : null;
		String headerImageUrl = input_map.containsKey("headerImageUrl") ?
			input_map.get("headerImageUrl").toString() : null;
		Boolean isPrimaryConsortium = input_map.containsKey("isPrimaryConsortium") ?
			Boolean.parseBoolean(input_map.get("isPrimaryConsortium").toString()) : null;
		String headerImageUploader = env.getGraphQlContext().get("userName");
		String headerImageUploaderEmail = env.getGraphQlContext().get("userEmail");
		Collection<String> roles = env.getGraphQlContext().get("roles");

		// Check if the user has the required role to edit consortium information
		if (roles == null || (!roles.contains("ADMIN") && !roles.contains("CONSORTIUM_ADMIN") && !roles.contains("LIBRARY_ADMIN"))) {
			log.warn("updateConsortiumDataFetcher: Access denied for user {}: user does not have the required role to update a consortium.", userString);
			throw new HttpStatusException(HttpStatus.UNAUTHORIZED, "Access denied: you do not have the required role to perform this action.");		}

		Mono<Consortium> transactionMono = Mono.from(r2dbcOperations.withTransaction(status ->
			Mono.from(consortiumRepository.findById(id))
				.flatMap(consortium -> {
					if (displayName != null) {
						consortium.setDisplayName(displayName);
					}
					if (isPrimaryConsortium !=null ) {
						consortium.setIsPrimaryConsortium(isPrimaryConsortium);
					}
					// If a new URL is provided, set the user info for the upload so we know who uploaded it
					if (headerImageUrl != null) {
						consortium.setHeaderImageUrl(headerImageUrl);
						consortium.setHeaderImageUploader(headerImageUploader);
						consortium.setHeaderImageUploaderEmail(headerImageUploaderEmail);
					}
					consortium.setLastEditedBy(userString);
					changeReferenceUrl.ifPresent(consortium::setChangeReferenceUrl);
					changeCategory.ifPresent(consortium::setChangeCategory);
					reason.ifPresent(consortium::setReason);
					return Mono.from(consortiumRepository.update(consortium));
				})
		));

		return transactionMono.toFuture();
	}
}
