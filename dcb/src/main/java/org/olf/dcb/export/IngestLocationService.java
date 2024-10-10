package org.olf.dcb.export;

import java.util.List;

import org.olf.dcb.core.model.Location;
import org.olf.dcb.export.model.IngestResult;
import org.olf.dcb.export.model.ProcessingResult;
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
public class IngestLocationService {
	private static final Logger log = LoggerFactory.getLogger(IngestLocationService.class);
	
	private final LocationRepository locationRepository;
	
	public IngestLocationService(
			LocationRepository locationRepository
	) {
		this.locationRepository = locationRepository;
	}

	public void ingest(
		SiteConfiguration siteConfiguration,
		IngestResult ingestResult 
	) {
		// Process the location records they want to import
		List<Location> locations = siteConfiguration.locations;
		if ((locations != null) && !locations.isEmpty()) {
			Flux.fromIterable(locations)
				.doOnError(e -> {
					String errorMessage = "Exception while processing locations for ingest: " + e.toString();
					log.error(errorMessage, e);
					ingestResult.messages.add(errorMessage);
				})
				.flatMap(location -> processDataLocation(location, ingestResult.locations))
				.blockLast();
		}
	}

	private Mono<Location> processDataLocation(
		Location location,
		ProcessingResult processingResult 
	) {
		return(Mono.from(locationRepository.existsById(location.getId()))
				.flatMap(exists -> Mono.fromDirect(exists ? locationRepository.update(location) : locationRepository.save(location)))
			.doOnSuccess(a -> {
				processingResult.success(location.getId().toString(), location.getName());
			})
			.doOnError(e -> {
				processingResult.failed(location.getId().toString(), location.getName(), e.toString());
			})
			.then(Mono.just(location))
		);
	}
}
