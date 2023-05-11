package org.olf.reshare.dcb.request.fulfilment;

import org.olf.reshare.dcb.core.model.Patron;

import io.micronaut.context.annotation.Prototype;
import reactor.core.publisher.Mono;

@Prototype
public class FindOrCreatePatronService {
	private final PatronService patronService;

	public FindOrCreatePatronService(PatronService patronService) {
		this.patronService = patronService;
	}

	public Mono<Patron> findOrCreatePatron(String localSystemCode, String localId) {
		return patronService.findPatronFor(localSystemCode, localId)
			.switchIfEmpty(Mono.defer(() -> patronService.createPatron(localSystemCode, localId)));
	}
}
