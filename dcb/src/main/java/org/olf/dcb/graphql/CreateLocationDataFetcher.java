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
import org.olf.dcb.storage.AgencyRepository;
import org.olf.dcb.storage.HostLmsRepository;
import org.olf.dcb.storage.LocationRepository;
import reactor.core.publisher.Mono;
import services.k_int.utils.UUIDUtils;

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
		// Take our key location information here.

		String code = input_map.get("code") !=null ? input_map.get("code").toString() : null;
		String name = input_map.get("name") !=null ? input_map.get("name").toString() : null;
		String type = input_map.get("type") !=null ? input_map.get("type").toString() : null;
		Boolean isPickup = input_map.get("isPickup") !=null ? Boolean.valueOf(input_map.get("isPickup").toString()): null;
		Boolean isShelving = input_map.get("isShelving") !=null ? Boolean.valueOf(input_map.get("isShelving").toString()): null;
		Boolean isSupplying = input_map.get("isSupplying") !=null ? Boolean.valueOf(input_map.get("isSupplying").toString()): null;

		String importReference = input_map.get("importReference") !=null ? input_map.get("importReference").toString() : null;
		String printLabel = input_map.get("printLabel") !=null ? input_map.get("printLabel").toString() : null;
		String deliveryStops = input_map.get("deliveryStops") !=null ? input_map.get("deliveryStops").toString() : null;
		String localId = input_map.get("localId") !=null ? input_map.get("localId").toString() : null;


		// Make sure these values are valid lat/long
		Double latitude = input_map.get("latitude") != null ? Double.valueOf(input_map.get("latitude").toString()) : null;
		Double longitude = input_map.get("longitude") != null ? Double.valueOf(input_map.get("longitude").toString()) : null;

		// Then take an agency code and a Host LMS code. This is required for non-HUB locations.
		String agencyCode = input_map.get("agencyCode") !=null ? input_map.get("agencyCode").toString() : null;
		// Can this be restricted to only the Host LMS on the agency??
		String hostLmsCode = input_map.get("hostLmsCode") !=null ? input_map.get("hostLmsCode").toString() : null;
		// Get parent Location ID as optional
		String parentLocation = input_map.get("parentLocation") !=null ? input_map.get("parentLocation").toString() : null;

		// Roles and reasons
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

		// Now we must validate the location

		// Does it have a Host LMS
		// Does it have an agency
		// Does one with the same local ID OR code already exist
		// Is it of type NOT pickup - in which case we should throw an error saying we only support adding pickup locations

		return Mono.from(agencyRepository.findOneByCode(agencyCode))
			.switchIfEmpty(Mono.error(new EntityCreationException(
				"Location creation failed: associated agency not found. You must supply a valid agency code.")))
			.flatMap(agency -> Mono.from(hostLmsRepository.findByCode(hostLmsCode))
				.switchIfEmpty(Mono.error(new EntityCreationException(
					"Location creation failed: associated Host LMS not found. You must supply a valid Host LMS code.")))
				.flatMap(hostLms -> {
					// Agency and Host LMS both exist so we can create a location
					Location location = Location.builder()
						.id(UUIDUtils.generateLocationId(agencyCode, hostLmsCode))
						.name(name)
						.code(code)
						.type(type)
						.isPickup(isPickup)
						.isShelving(isShelving)
						.isSupplyingLocation(isSupplying)
						.importReference(importReference)
						.printLabel(printLabel)
						.deliveryStops(deliveryStops)
						.localId(localId) // this is the id from the spreadsheet
						.latitude(latitude)
						.longitude(longitude)
						.lastEditedBy(userString)
						.agency(agency)
						.hostSystem(hostLms).build();

					changeReferenceUrl.ifPresent(location::setChangeReferenceUrl);
					changeCategory.ifPresent(location::setChangeCategory);
					reason.ifPresent(location::setReason);
					return Mono.from(r2dbcOperations.withTransaction(status -> Mono.from(locationRepository.saveOrUpdate(location))));

				})).toFuture();
	}
}
