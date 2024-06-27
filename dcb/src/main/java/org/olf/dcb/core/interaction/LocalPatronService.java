package org.olf.dcb.core.interaction;

import static reactor.core.publisher.Mono.defer;

import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.model.DataAgency;
import org.olf.dcb.core.model.ReferenceValueMapping;
import org.olf.dcb.core.svc.AgencyService;
import org.olf.dcb.core.svc.LocationToAgencyMappingService;
import org.olf.dcb.request.workflow.exceptions.UnableToResolveAgencyProblem;
import org.olf.dcb.utils.PropertyAccessUtils;

import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;

@Slf4j
@Singleton
public class LocalPatronService {
	private final LocationToAgencyMappingService locationToAgencyMappingService;
	private final AgencyService agencyService;
	private final HostLmsService hostLmsService;

	public LocalPatronService(LocationToAgencyMappingService locationToAgencyMappingService,
		AgencyService agencyService, HostLmsService hostLmsService) {

		this.locationToAgencyMappingService = locationToAgencyMappingService;
		this.agencyService = agencyService;
		this.hostLmsService = hostLmsService;
	}

	public Mono<Tuple2<Patron, DataAgency>> findLocalPatronAndAgency(
		String localPatronId, String hostLmsCode) {

		return hostLmsService.getClientFor(hostLmsCode)
			.flatMap(client -> client.getPatronByLocalId(localPatronId))
			.doOnSuccess(patron -> log.debug("Found patron: {} from Host LMS: {}", patron, hostLmsCode))
			// Could be done inside the Host LMS client method
			// Was not done initially due to potentially affecting other uses
			.filter(Patron::isNotDeleted)
			// This uses a tuple because the patron does not directly have an association with an agency
			.zipWhen(patron -> findAgencyForPatron(patron, hostLmsCode));
	}

	private Mono<DataAgency> findAgencyForPatron(Patron patron, String hostLmsCode) {
		return findHomeLocationMapping(patron, hostLmsCode)
			.switchIfEmpty(defer(() -> findDefaultAgencyCode(hostLmsCode)))
			.flatMap(agencyService::findByCode)
			.switchIfEmpty(UnableToResolveAgencyProblem.raiseError(
				patron.getLocalHomeLibraryCode(), hostLmsCode));
	}

	private Mono<String> findHomeLocationMapping(Patron patron, String hostLmsCode) {
		log.debug("Finding home location mapping for host LMS code: \"{}\", patron: {}", hostLmsCode, patron);

		return locationToAgencyMappingService.findLocationToAgencyMapping(
				hostLmsCode, PropertyAccessUtils.getValueOrNull(patron, Patron::getLocalHomeLibraryCode))
			.map(ReferenceValueMapping::getToValue);
	}

	private Mono<String> findDefaultAgencyCode(String hostLmsCode) {
		return locationToAgencyMappingService.findDefaultAgencyCode(hostLmsCode);
	}
}
