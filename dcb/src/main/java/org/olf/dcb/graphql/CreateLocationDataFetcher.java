package org.olf.dcb.graphql;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import io.micronaut.data.r2dbc.operations.R2dbcOperations;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.exceptions.HttpStatusException;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.olf.dcb.core.api.exceptions.EntityCreationException;
import org.olf.dcb.core.model.Location;
import org.olf.dcb.graphql.validation.LocationInputValidator;
import org.olf.dcb.storage.AgencyRepository;
import org.olf.dcb.storage.HostLmsRepository;
import org.olf.dcb.storage.LocationRepository;
import reactor.core.publisher.Mono;
import services.k_int.utils.UUIDUtils;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@Singleton
@Slf4j
public class CreateLocationDataFetcher implements DataFetcher<CompletableFuture<Location>> {
	private final LocationRepository locationRepository;
	private final AgencyRepository agencyRepository;

	private final HostLmsRepository hostLmsRepository;
	private final R2dbcOperations r2dbcOperations;

	public CreateLocationDataFetcher(LocationRepository locationRepository, AgencyRepository agencyRepository, HostLmsRepository hostLmsRepository, R2dbcOperations r2dbcOperations) {
		this.locationRepository = locationRepository;
		this.agencyRepository = agencyRepository;
		this.hostLmsRepository = hostLmsRepository;
		this.r2dbcOperations = r2dbcOperations;
	}

	@Override
	public CompletableFuture<Location> get(DataFetchingEnvironment env) {
		Map<String, Object> input_map = env.getArgument("input");
		log.debug("createLocationDataFetcher {}", input_map);

		String code = input_map.get("code") !=null ? input_map.get("code").toString() : null;
		String name = input_map.get("name") !=null ? input_map.get("name").toString() : null;
		String type = input_map.get("type") !=null ? input_map.get("type").toString() : null;
		Boolean isPickup = input_map.get("isPickup") !=null ? Boolean.valueOf(input_map.get("isPickup").toString()): null;
		Boolean isPickupAnywhere = input_map.get("isPickupAnywhere") !=null ? Boolean.valueOf(input_map.get("isPickupAnywhere").toString()): null;
		String localId = input_map.get("localId") !=null ? input_map.get("localId").toString() : null;
		String agencyCode = input_map.get("agencyCode") !=null ? input_map.get("agencyCode").toString() : null;
		String hostLmsCode = input_map.get("hostLmsCode") !=null ? input_map.get("hostLmsCode").toString() : null;

		String printLabel = input_map.get("printLabel") !=null ? input_map.get("printLabel").toString() : "";
		String deliveryStops = input_map.get("deliveryStops") !=null ? input_map.get("deliveryStops").toString() : "";

		Double latitude = input_map.get("latitude") != null ? Double.valueOf(input_map.get("latitude").toString()) : null; // Must be -90 to 90
		Double longitude = input_map.get("longitude") != null ? Double.valueOf(input_map.get("longitude").toString()) : null; // Must be -180 to 180

		String userString = Optional.ofNullable(env.getGraphQlContext().get("userName"))
			.map(Object::toString)
			.orElse("User not detected");
		Collection<String> roles = env.getGraphQlContext().get("roles");


		if (roles == null || (!roles.contains("ADMIN") && !roles.contains("CONSORTIUM_ADMIN") && !roles.contains("LIBRARY_ADMIN"))) {
			log.warn("createLocation: Access denied for user {}: user does not have the required role to create a location.", userString);
			throw new HttpStatusException(HttpStatus.UNAUTHORIZED, "Access denied: you do not have the required role to perform this action.");		}

		Optional<String> reason = Optional.ofNullable(input_map.get("reason"))
			.map(Object::toString);
		Optional<String> changeReferenceUrl = Optional.ofNullable(input_map.get("changeReferenceUrl"))
			.map(Object::toString);
		Optional<String> changeCategory = Optional.ofNullable(input_map.get("changeCategory"))
			.map(Object::toString);

		return Mono.from(agencyRepository.findOneByCode(agencyCode))
			.switchIfEmpty(Mono.error(new EntityCreationException(
				"Location creation failed: associated agency not found. You must supply a valid agency code.")))
			.flatMap(agency -> Mono.from(hostLmsRepository.findByCode(hostLmsCode))
				.switchIfEmpty(Mono.error(new EntityCreationException(
					"Location creation failed: associated Host LMS not found. You must supply a valid Host LMS code.")))
				.flatMap(hostLms -> LocationInputValidator.validateInput(input_map, hostLms)
					.then(Mono.from(locationRepository.existsByCode(code)))
					.flatMap(codeExists -> {
						if (codeExists) {
							return Mono.error(new IllegalArgumentException("Location with this code already exists"));
						}
						return localId != null ?
							Mono.from(locationRepository.existsByLocalIdAndHostSystem(localId, hostLms))
								.flatMap(localIdExists -> {
									if (localIdExists) {
										return Mono.error(new IllegalArgumentException("Location with this localId already exists"));
									}
									return Mono.just(hostLms);
								})
							: Mono.just(hostLms);
					}))
				.flatMap(hostLms -> {
					// Now we know agency and host LMS both exist, we can create the location
					Location location = Location.builder()
						.id(UUIDUtils.generateLocationId(agencyCode, code))
						.name(name)
						.code(code)
						.type(type)
						.isPickup(isPickup)
						.isEnabledForPickupAnywhere(isPickupAnywhere)
						.printLabel(printLabel.isBlank() ? name : printLabel)
						.deliveryStops(deliveryStops.isBlank() ? agencyCode : deliveryStops)
						.localId(localId)
						.latitude(latitude)
						.longitude(longitude)
						.lastEditedBy(userString)
						.agency(agency)
						.lastImported(Instant.now())
						.hostSystem(hostLms).build();
					changeReferenceUrl.ifPresent(location::setChangeReferenceUrl);
					changeCategory.ifPresent(location::setChangeCategory);
					reason.ifPresent(location::setReason);
					return Mono.from(r2dbcOperations.withTransaction(status -> Mono.from(locationRepository.saveOrUpdate(location))));
				})).toFuture();
	}
}
