package org.olf.dcb.graphql;

import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import io.micronaut.data.r2dbc.operations.R2dbcOperations;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.exceptions.HttpStatusException;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.olf.dcb.core.api.exceptions.EntityCreationException;
import org.olf.dcb.core.model.DataAgency;
import org.olf.dcb.storage.AgencyRepository;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

// While we are moving away from exposing agencies, unfortunately we still have important stuff like lat/longs on them
// This aims to provide one data fetcher for all updates to agency, so the others can carefully be deprecated once this is live on all systems.
@Singleton
@Slf4j
public class UpdateAgencyDataFetcher implements DataFetcher<CompletableFuture<DataAgency>> {


	private final AgencyRepository agencyRepository;
	private final R2dbcOperations r2dbcOperations;
	private static final double MIN_LATITUDE = -90.0;
	private static final double MAX_LATITUDE = 90.0;
	private static final double MIN_LONGITUDE = -180.0;
	private static final double MAX_LONGITUDE = 180.0;

	public UpdateAgencyDataFetcher(AgencyRepository agencyRepository, R2dbcOperations r2dbcOperations) {
		this.agencyRepository = agencyRepository;
		this.r2dbcOperations = r2dbcOperations;
	}


	// Updates the lat/longs
	@Override
	public CompletableFuture<DataAgency> get(DataFetchingEnvironment env) {
		Map<String, Object> input_map = env.getArgument("input");
		log.debug("UpdateAgencyDataFetcher {}", input_map);

		// Role check. Only consortium administrators can change this.
		Collection<String> roles = env.getGraphQlContext().get("roles");
		String userString = Optional.ofNullable(env.getGraphQlContext().get("userName"))
			.map(Object::toString)
			.orElse("User not detected");

		if (roles == null || (!roles.contains("CONSORTIUM_ADMIN"))) {
			log.warn("createRoleDataFetcher: Access denied for user {}: user does not have the required role to update consortial max loans.", userString);
			throw new HttpStatusException(HttpStatus.UNAUTHORIZED, "Access denied: you do not have the required role to update consortial max loans.");
		}

		// Get the agency code and the editable values
		String code = input_map.get("code").toString();
		Double latitude = input_map.containsKey("latitude") ?
			Double.valueOf(input_map.get("longitude").toString()): null;
		Double longitude = input_map.containsKey("longitude") ?
			Double.valueOf(input_map.get("longitude").toString()) : null;
		Optional<Boolean> isSupplyingAgency = Optional.ofNullable(input_map.get("isSupplyingAgency"))
			.map(value -> Boolean.parseBoolean(value.toString()));
		Optional<Boolean> isBorrowingAgency = Optional.ofNullable(input_map.get("isBorrowingAgency"))
			.map(value -> Boolean.parseBoolean(value.toString()));
		Integer maxLoansInput = input_map.containsKey("maxConsortialLoans") ?
			Integer.parseInt(input_map.get("maxConsortialLoans").toString()): null; // Needs valid integer check
		Optional <String> authProfile = Optional.ofNullable(env.getGraphQlContext().get("authProfile"))
			.map(Object::toString);
		Optional <String> name = Optional.ofNullable(env.getGraphQlContext().get("name"))
			.map(Object::toString);


		// Get data change log values
		Optional<String> reason = Optional.ofNullable(input_map.get("reason"))
			.map(Object::toString);
		Optional<String> changeReferenceUrl = Optional.ofNullable(input_map.get("changeReferenceUrl"))
			.map(Object::toString);
		Optional<String> changeCategory = Optional.ofNullable(input_map.get("changeCategory"))
			.map(Object::toString);

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
					authProfile.ifPresent(value -> {
						agency.setAuthProfile(value);
						agency.setLastEditedBy(userString);
					});
					name.ifPresent(value -> {
						agency.setName(value);
						agency.setLastEditedBy(userString);
					});
					if (latitude != null) {
						if (latitude < MIN_LATITUDE || latitude > MAX_LATITUDE) {
							return Mono.error(new EntityCreationException(
								"Latitude update failed: latitude must be between -90 and 90"));
						}
						else
						{
							agency.setLatitude(latitude);
							agency.setLastEditedBy(userString);
						}
					}
					if (longitude !=  null) {
						if (longitude < MIN_LONGITUDE || longitude > MAX_LONGITUDE) {
							return Mono.error(new EntityCreationException(
								"Longitude update failed: longitude must be between -180 and 180"));
						}
						else
						{
							agency.setLongitude(longitude);
							agency.setLastEditedBy(userString);
						}
					}
					if (maxLoansInput !=null) {
						agency.setMaxConsortialLoans(maxLoansInput);
						agency.setLastEditedBy(userString);
					}
					return Mono.from(agencyRepository.update(agency));
				})
		));

		return transactionMono.toFuture();
	}
}
