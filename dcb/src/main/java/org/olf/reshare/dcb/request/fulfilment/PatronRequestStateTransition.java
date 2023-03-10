package org.olf.reshare.dcb.request.fulfilment;

import org.olf.reshare.dcb.core.model.PatronRequest;
import reactor.core.publisher.Mono;

public interface PatronRequestStateTransition {
	Mono<PatronRequest> makeTransition(PatronRequest patronRequest);
}
