package org.olf.dcb.core.interaction;

import static org.olf.dcb.utils.PropertyAccessUtils.getValueOrNull;

import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.model.DataAgency;
import org.olf.dcb.core.svc.LocationToAgencyMappingService;

import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;

@Slf4j
@Singleton
public class LocalPatronService {
	private final LocationToAgencyMappingService locationToAgencyMappingService;
	private final HostLmsService hostLmsService;

	public LocalPatronService(LocationToAgencyMappingService locationToAgencyMappingService,
		HostLmsService hostLmsService) {

		this.locationToAgencyMappingService = locationToAgencyMappingService;
		this.hostLmsService = hostLmsService;
	}

	public Mono<Tuple2<Patron, DataAgency>> findLocalPatronAndAgency(
		String localPatronId, String hostLmsCode) {

		return hostLmsService.getClientFor(hostLmsCode)
			.flatMap(client -> getPatronByIdentifier(localPatronId, client))
			.doOnSuccess(patron -> log.info("Found patron: {} from Host LMS: {}", patron, hostLmsCode))
			// Could be done inside the Host LMS client method
			// Was not done initially due to potentially affecting other uses
			.filter(Patron::isNotDeleted)
			// This uses a tuple because the patron does not directly have an association with an agency
			.zipWhen(patron -> findAgencyForPatron(patron, hostLmsCode));
	}

	private Mono<Patron> getPatronByIdentifier(String identifier, HostLmsClient client) {
		log.info("Getting patron by local id {}", identifier);

		return client.getPatronByIdentifier(identifier)
			.doOnSuccess(patron -> log.info("Found patron by ID: {}", patron))
			.doOnError(error -> log.error("Getting patron by identifier '{}' failed with error", identifier, error));
	}

	private Mono<DataAgency> findAgencyForPatron(Patron patron, String hostLmsCode) {
		log.info("Finding home location mapping for host LMS code: \"{}\", patron: {}", hostLmsCode, patron);

		return locationToAgencyMappingService.resolveAgencyForPatronHomeLocation(
			hostLmsCode, getValueOrNull(patron, Patron::getLocalHomeLibraryCode));
	}
}
