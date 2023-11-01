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
}
