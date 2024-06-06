package org.olf.dcb.core.interaction;

import static org.olf.dcb.utils.PropertyAccessUtils.getValue;

import org.olf.dcb.core.model.ReferenceValueMapping;
import org.olf.dcb.core.svc.LocationToAgencyMappingService;

import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Singleton
public class LocalPatronService {
	private final LocationToAgencyMappingService locationToAgencyMappingService;

	public LocalPatronService(LocationToAgencyMappingService locationToAgencyMappingService) {
		this.locationToAgencyMappingService = locationToAgencyMappingService;
	}

	public Mono<String> findHomeLocationMapping(Patron patron, String hostLmsCode) {
		log.debug("Finding home location mapping for host LMS code: \"{}\", patron: {}", hostLmsCode, patron);

		return locationToAgencyMappingService.findLocationToAgencyMapping(
				hostLmsCode, getValue(patron, Patron::getLocalHomeLibraryCode))
			.map(ReferenceValueMapping::getToValue);
	}
}
