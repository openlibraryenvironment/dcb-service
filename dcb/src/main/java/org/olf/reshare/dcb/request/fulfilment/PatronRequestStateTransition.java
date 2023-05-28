package org.olf.reshare.dcb.request.fulfilment;

import org.olf.reshare.dcb.core.model.PatronRequest;

import reactor.core.publisher.Mono;

public interface PatronRequestStateTransition {

	// When can this tranistion take place
	String getGuardCondition();

	Mono<PatronRequest> attempt(PatronRequest patronRequest);
}
