package org.olf.dcb.graphql;

import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import io.micronaut.data.r2dbc.operations.R2dbcOperations;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.exceptions.HttpStatusException;
import jakarta.inject.Singleton;
import org.olf.dcb.core.model.Consortium;
import org.olf.dcb.core.model.FunctionalSetting;
import org.olf.dcb.storage.ConsortiumRepository;
import org.olf.dcb.storage.FunctionalSettingRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.Map;
import java.util.concurrent.CompletableFuture;


@Singleton
public class UpdateFunctionalSettingDataFetcher implements DataFetcher<CompletableFuture<FunctionalSetting>> {

	private final FunctionalSettingRepository functionalSettingRepository;

	private final ConsortiumRepository consortiumRepository;

	private final R2dbcOperations r2dbcOperations;

	private static Logger log = LoggerFactory.getLogger(DataFetchers.class);


	public UpdateFunctionalSettingDataFetcher(FunctionalSettingRepository functionalSettingRepository, ConsortiumRepository consortiumRepository, R2dbcOperations r2dbcOperations) {
		this.functionalSettingRepository = functionalSettingRepository;
		this.consortiumRepository = consortiumRepository;
		this.r2dbcOperations = r2dbcOperations;
	}

	@Override
	public CompletableFuture<FunctionalSetting> get(DataFetchingEnvironment env) {
		Map<String, Object> input_map = env.getArgument("input");
		log.debug("updateFunctionalSettingDataFetcher {}", input_map);

		UUID id = input_map.get("id") != null ? UUID.fromString(input_map.get("id").toString()) : null;
		// Only allow changes to enabled + description at present. Name should never be changed as it's used for UUID generation.
		Boolean enabled = input_map.containsKey("enabled") ?
			Boolean.parseBoolean(input_map.get("enabled").toString()) : null;
		String description = input_map.get("description") != null ? input_map.get("description").toString() : null;

	 // For the data change log
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

		// If this is ever expanded to libraries, we will need to also supply context for potentially different role-based access here.
		if (roles == null || (!roles.contains("ADMIN") && !roles.contains("CONSORTIUM_ADMIN"))) {
			log.warn("updateFunctionalSettingDataFetcher: Access denied for user {}: user does not have the required role to update a functional setting", userString);
			throw new HttpStatusException(HttpStatus.UNAUTHORIZED, "Access denied: you do not have the required role to perform this action.");		}

		if (id == null)
		{
			log.warn("No valid UUID provided. User cannot update functional setting.");
			throw new HttpStatusException(HttpStatus.BAD_REQUEST, "You must provide a valid UUID to update functional settings");
		}
		Mono<FunctionalSetting> transactionMono = Mono.from(r2dbcOperations.withTransaction(status ->
			Mono.from(functionalSettingRepository.findById(id))
				.flatMap(functionalSetting -> {
					if (enabled != null) {
						functionalSetting.setEnabled(enabled);
					}
					if (description != null) {
						functionalSetting.setDescription(description);
					}
					functionalSetting.setLastEditedBy(userString);
					changeReferenceUrl.ifPresent(functionalSetting::setChangeReferenceUrl);
					changeCategory.ifPresent(functionalSetting::setChangeCategory);
					reason.ifPresent(functionalSetting::setReason);
					return Mono.from(functionalSettingRepository.update(functionalSetting));
				})
		));
		return transactionMono.toFuture();
	}
}

