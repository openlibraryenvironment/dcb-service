package org.olf.dcb.core.svc;

import java.util.UUID;

import org.olf.dcb.core.model.Location;
import org.olf.dcb.storage.LocationRepository;

import jakarta.inject.Singleton;
import reactor.core.publisher.Mono;

@Singleton
public class LocationService {
	private final LocationRepository locationRepository;

	public LocationService(LocationRepository locationRepository) {
		this.locationRepository = locationRepository;
	}

	public Mono<Location> findById(UUID id) {
		return Mono.from(locationRepository.findById(id));
	}

	public Mono<Location> findByCode(String pickupLocationCode) {
		return Mono.from(locationRepository.findOneByCode(pickupLocationCode));
	}

	/**
	 * Attempt to convert string ID into a UUID before finding by ID
	 *
	 * @param id the ID to find a location for as a string
	 * @return empty if the ID is not a UUID, otherwise the result of finding a location by ID
	 */
	public Mono<Location> findById(String id) {
		try {
			final var parsedId = UUID.fromString(id);

			return findById(parsedId);
		}
		// Is not a UUID
		catch (IllegalArgumentException e) {
			return Mono.empty();
		}
	}
}
