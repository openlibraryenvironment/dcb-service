package org.olf.dcb.export;

import java.util.Collection;
import java.util.UUID;

import org.olf.dcb.core.model.Location;
import org.olf.dcb.export.model.SiteConfiguration;
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

	public void export(
		Collection<UUID> hostLmsIds,
		SiteConfiguration siteConfiguration
	) {
		// Process the locations associated with the exported host lms
		Flux.from(locationRepository.findByHostLmsIds(hostLmsIds))
			.doOnError(e -> {
				String errorMessage = "Exception while processing locations for export: " + e.toString();
				log.error(errorMessage, e);
				siteConfiguration.errors.add(errorMessage);
			})
			.flatMap(location -> processDataLocation(location, siteConfiguration))
			.blockLast();
	}

	private Mono<Location> processDataLocation(
			Location location,
			SiteConfiguration siteConfiguration
	) {
		siteConfiguration.locations.add(location);
		return(Mono.just(location));
	}
}
