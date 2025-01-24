package org.olf.dcb.graphql;

import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import io.micronaut.data.r2dbc.operations.R2dbcOperations;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.exceptions.HttpStatusException;
import jakarta.inject.Singleton;
import org.olf.dcb.core.model.ReferenceValueMapping;

import org.olf.dcb.storage.ReferenceValueMappingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;


@Singleton
public class UpdateReferenceValueMappingDataFetcher implements DataFetcher<CompletableFuture<ReferenceValueMapping>> {

	private final ReferenceValueMappingRepository referenceValueMappingRepository;

	private final R2dbcOperations r2dbcOperations;

	private static Logger log = LoggerFactory.getLogger(DataFetchers.class);


	public UpdateReferenceValueMappingDataFetcher(ReferenceValueMappingRepository referenceValueMappingRepository, R2dbcOperations r2dbcOperations) {
		this.referenceValueMappingRepository = referenceValueMappingRepository;
		this.r2dbcOperations = r2dbcOperations;
	}
	@Override
	public CompletableFuture<ReferenceValueMapping> get(DataFetchingEnvironment env) {
		Map<String, Object> input_map = env.getArgument("input");
		log.debug("updateReferenceValueMappingDataFetcher {}", input_map);

		UUID id = input_map.get("id") != null ? UUID.fromString(input_map.get("id").toString()) : null;
		String toValue = input_map.get("toValue") != null ? input_map.get("toValue").toString() : null;

		// For data change log
		Optional<String> reason = Optional.ofNullable(input_map.get("reason"))
			.map(Object::toString);
		Optional<String> changeReferenceUrl = Optional.ofNullable(input_map.get("changeReferenceUrl"))
			.map(Object::toString);
		Optional<String> changeCategory = Optional.ofNullable(input_map.get("changeCategory"))
			.map(Object::toString);
		String userString = Optional.ofNullable(env.getGraphQlContext().get("userName"))
			.map(Object::toString)
			.orElse("User not detected");
		Collection<String> roles = env.getGraphQlContext().get("roles");


		if (roles == null || (!roles.contains("ADMIN") && !roles.contains("CONSORTIUM_ADMIN") && !roles.contains("LIBRARY_ADMIN"))) {
			log.warn("updateReferenceValueMappingDataFetcher: Access denied for user {}: user does not have the required role to update a referenceValueMapping.", userString);
			throw new HttpStatusException(HttpStatus.UNAUTHORIZED, "Access denied: you do not have the required role to perform this action.");		}

		Mono<ReferenceValueMapping> transactionMono = Mono.from(r2dbcOperations.withTransaction(status ->
			Mono.from(referenceValueMappingRepository.findById(id))
				.flatMap(referenceValueMapping -> {
					if (toValue != null) {
						referenceValueMapping.setToValue(toValue);
					}
					referenceValueMapping.setLastEditedBy(userString);
					changeReferenceUrl.ifPresent(referenceValueMapping::setChangeReferenceUrl);
					changeCategory.ifPresent(referenceValueMapping::setChangeCategory);
					reason.ifPresent(referenceValueMapping::setReason);
					return Mono.from(referenceValueMappingRepository.update(referenceValueMapping));
				})
		));

		return transactionMono.toFuture();
	}
}
