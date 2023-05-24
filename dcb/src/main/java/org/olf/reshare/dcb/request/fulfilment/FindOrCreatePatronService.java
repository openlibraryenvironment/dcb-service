package org.olf.reshare.dcb.request.fulfilment;

import org.olf.reshare.dcb.core.model.Patron;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micronaut.context.annotation.Prototype;
import reactor.core.publisher.Mono;

@Prototype
public class FindOrCreatePatronService {
	private static final Logger log = LoggerFactory.getLogger(FindOrCreatePatronService.class);

	private final PatronService patronService;

	public FindOrCreatePatronService(PatronService patronService) {
		this.patronService = patronService;
	}

	public Mono<Patron> findOrCreatePatron(String localSystemCode, String localId,
		String homeLibraryCode) {

		log.debug("findOrCreatePatron({}, {}, {})", localSystemCode, localId, homeLibraryCode);

		return patronService.findPatronFor(localSystemCode, localId)
			.switchIfEmpty(createPatron(localSystemCode, localId, homeLibraryCode))
			.flatMap(patronService::findById)
			.doOnSuccess(patron -> log.debug("Found or created patron: {}", patron))
			.doOnError(error -> log.debug("Error when finding or creating patron"));
	}

	private Mono<PatronService.PatronId> createPatron(String localSystemCode,
		String localId, String homeLibraryCode) {

		return Mono.defer(() -> patronService
			.createPatron(localSystemCode, localId, homeLibraryCode));
	}
}
