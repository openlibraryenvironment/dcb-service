package org.olf.dcb.core.interaction;

import static org.olf.dcb.utils.PropertyAccessUtils.getValue;
import static reactor.core.publisher.Mono.defer;

import org.olf.dcb.core.model.DataAgency;
import org.olf.dcb.core.model.ReferenceValueMapping;
import org.olf.dcb.core.svc.AgencyService;
import org.olf.dcb.core.svc.LocationToAgencyMappingService;
import org.olf.dcb.request.workflow.exceptions.UnableToResolveAgencyProblem;

import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Singleton
public class LocalPatronService {
	private final LocationToAgencyMappingService locationToAgencyMappingService;
	private final AgencyService agencyService;

	public LocalPatronService(LocationToAgencyMappingService locationToAgencyMappingService,
		AgencyService agencyService) {

		this.locationToAgencyMappingService = locationToAgencyMappingService;
		this.agencyService = agencyService;
	}

	public Mono<DataAgency> findAgencyForPatron(Patron patron, String hostLmsCode) {
		return findHomeLocationMapping(patron, hostLmsCode)
			.switchIfEmpty(defer(() -> findDefaultAgencyCode(hostLmsCode)))
			.flatMap(agencyService::findByCode)
			.switchIfEmpty(UnableToResolveAgencyProblem.raiseError(
				patron.getLocalHomeLibraryCode(), hostLmsCode));
	}

	private Mono<String> findHomeLocationMapping(Patron patron, String hostLmsCode) {
		log.debug("Finding home location mapping for host LMS code: \"{}\", patron: {}", hostLmsCode, patron);

		return locationToAgencyMappingService.findLocationToAgencyMapping(
				hostLmsCode, getValue(patron, Patron::getLocalHomeLibraryCode))
			.map(ReferenceValueMapping::getToValue);
	}

	private Mono<String> findDefaultAgencyCode(String hostLmsCode) {
		return locationToAgencyMappingService.findDefaultAgencyCode(hostLmsCode);
	}
}
