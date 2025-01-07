package org.olf.dcb.graphql;

import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import io.micronaut.data.r2dbc.operations.R2dbcOperations;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.exceptions.HttpStatusException;
import jakarta.inject.Singleton;
import org.olf.dcb.core.model.DataAgency;
import org.olf.dcb.storage.AgencyRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Singleton
public class UpdateAgencyParticipationStatusDataFetcher implements DataFetcher<CompletableFuture<DataAgency>> {

	private final AgencyRepository agencyRepository;
	private final R2dbcOperations r2dbcOperations;

	private static Logger log = LoggerFactory.getLogger(DataFetchers.class);


	public UpdateAgencyParticipationStatusDataFetcher(AgencyRepository agencyRepository, R2dbcOperations r2dbcOperations) {
		this.agencyRepository = agencyRepository;
		this.r2dbcOperations = r2dbcOperations;
	}


	// Updates an agency's participation status depending on which is supplied
	@Override
	public CompletableFuture<DataAgency> get(DataFetchingEnvironment env) {
		Map<String, Object> input_map = env.getArgument("input");
		log.debug("updateAgencyParticipationStatusDataFetcher {}", input_map);

		String code = input_map.get("code").toString();

		Optional<Boolean> isSupplyingAgency = Optional.ofNullable(input_map.get("isSupplyingAgency"))
			.map(value -> Boolean.parseBoolean(value.toString()));
		Optional<Boolean> isBorrowingAgency = Optional.ofNullable(input_map.get("isBorrowingAgency"))
			.map(value -> Boolean.parseBoolean(value.toString()));
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

		// Check if the user has the required role
		if (roles == null || (!roles.contains("ADMIN") && !roles.contains("CONSORTIUM_ADMIN"))) {
			log.warn("updateAgencyParticipationStatusDataFetcher: Access denied for user {}: user does not have the required role to update a library's participation status.", userString);
			throw new HttpStatusException(HttpStatus.UNAUTHORIZED, "Access denied: you do not have the required role to perform this action.");
		}

		Mono<DataAgency> transactionMono = Mono.from(r2dbcOperations.withTransaction(status ->
			Mono.from(agencyRepository.findOneByCode(code))
				.flatMap(agency -> {
					reason.ifPresent(agency::setReason);
					changeCategory.ifPresent(agency::setChangeCategory);
					changeReferenceUrl.ifPresent(agency::setChangeReferenceUrl);
					isSupplyingAgency.ifPresent(value -> {
						agency.setIsSupplyingAgency(value);
						agency.setLastEditedBy(userString);
					});
					isBorrowingAgency.ifPresent(value -> {
						agency.setIsBorrowingAgency(value);
						agency.setLastEditedBy(userString);
					});
					return Mono.from(agencyRepository.update(agency));
				})
		));

		return transactionMono.toFuture();
	}
}
