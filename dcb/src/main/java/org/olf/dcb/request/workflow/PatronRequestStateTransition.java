package org.olf.dcb.request.workflow;

import org.olf.dcb.core.model.PatronRequest;

import reactor.core.publisher.Mono;

public interface PatronRequestStateTransition {
	
	boolean isApplicableFor(PatronRequest pr);

	Mono<PatronRequest> attempt(PatronRequest patronRequest);
}
