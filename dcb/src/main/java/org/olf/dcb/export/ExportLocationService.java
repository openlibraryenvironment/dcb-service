package org.olf.dcb.export;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.olf.dcb.core.model.Location;
import org.olf.dcb.storage.LocationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Singleton
public class ExportLocationService {
	private static final Logger log = LoggerFactory.getLogger(ExportLocationService.class);
	
	private final LocationRepository locationRepository;
	
	public ExportLocationService(
		LocationRepository locationRepository
	) {
		this.locationRepository = locationRepository;
	}

	public Map<String, Object> export(
		Collection<UUID> hostLmsIds,
		Map<String, Object> result,
		List<String> errors
	) {
		// Process the locations associated with the exported host lms
		List<Location> locations = new ArrayList<Location>();
		result.put("locations", locations);
		Flux.from(locationRepository.findByHostLmsIds(hostLmsIds))
			.doOnError(e -> {
				String errorMessage = "Exception while processing locations for export: " + e.toString();
				log.error(errorMessage, e);
				errors.add(errorMessage);
			})
			.flatMap(location -> processDataLocation(location, locations, errors))
			.blockLast();
		
		return(result);
	}

	private Mono<Location> processDataLocation(
			Location location,
			List<Location> locations,
			List<String> errors
	) {
		locations.add(location);
		return(Mono.just(location));
	}
}
