package org.olf.dcb.export;

import java.util.Collection;
import java.util.UUID;

import org.olf.dcb.core.model.Location;
import org.olf.dcb.export.model.SiteConfiguration;
import org.olf.dcb.storage.LocationRepository;

import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

@Slf4j
@Singleton
public class ExportLocationService {
	
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
			.map((Location location) -> {
				siteConfiguration.locations.add(location);
				return(location);
			})
			.blockLast();
	}
}
