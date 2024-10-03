package org.olf.dcb.graphql;

import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import io.micronaut.data.r2dbc.operations.R2dbcOperations;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.exceptions.HttpStatusException;
import jakarta.inject.Singleton;
import org.olf.dcb.core.model.NumericRangeMapping;

import org.olf.dcb.storage.NumericRangeMappingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;


@Singleton
public class UpdateNumericRangeMappingDataFetcher implements DataFetcher<CompletableFuture<NumericRangeMapping>> {

	private final NumericRangeMappingRepository numericRangeMappingRepository;

	private final R2dbcOperations r2dbcOperations;

	private static Logger log = LoggerFactory.getLogger(DataFetchers.class);


	public UpdateNumericRangeMappingDataFetcher(NumericRangeMappingRepository numericRangeMappingRepository, R2dbcOperations r2dbcOperations) {
		this.numericRangeMappingRepository = numericRangeMappingRepository;
		this.r2dbcOperations = r2dbcOperations;
	}
	@Override
	public CompletableFuture<NumericRangeMapping> get(DataFetchingEnvironment env) {
		Map<String, Object> input_map = env.getArgument("input");
		log.debug("updateNumericRangeMappingDataFetcher {}", input_map);

		UUID id = input_map.get("id") != null ? UUID.fromString(input_map.get("id").toString()) : null;
		String mappedValue = input_map.get("mappedValue") != null ? input_map.get("mappedValue").toString() : null;

		// For data change log
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


		if (roles == null || (!roles.contains("ADMIN") && !roles.contains("CONSORTIUM_ADMIN") && !roles.contains("LIBRARY_ADMIN"))) {
			log.warn("updateNumericRangeMappingDataFetcher: Access denied for user {}: user does not have the required role to update a numericRangeMapping.", userString);
			throw new HttpStatusException(HttpStatus.UNAUTHORIZED, "Access denied: you do not have the required role to perform this action.");		}

		Mono<NumericRangeMapping> transactionMono = Mono.from(r2dbcOperations.withTransaction(status ->
			Mono.from(numericRangeMappingRepository.findById(id))
				.flatMap(numericRangeMapping -> {
					if (mappedValue != null) {
						numericRangeMapping.setMappedValue(mappedValue);
					}
					numericRangeMapping.setLastEditedBy(userString);
					changeReferenceUrl.ifPresent(numericRangeMapping::setChangeReferenceUrl);
					changeCategory.ifPresent(numericRangeMapping::setChangeCategory);
					reason.ifPresent(numericRangeMapping::setReason);
					return Mono.from(numericRangeMappingRepository.update(numericRangeMapping));
				})
		));

		return transactionMono.toFuture();
	}
}
