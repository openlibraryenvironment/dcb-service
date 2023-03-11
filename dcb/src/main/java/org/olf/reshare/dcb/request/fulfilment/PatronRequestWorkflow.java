package org.olf.reshare.dcb.request.fulfilment;

import jakarta.inject.Singleton;
import org.olf.reshare.dcb.core.model.PatronRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

@Singleton
public class PatronRequestWorkflow {

	private static final Logger log = LoggerFactory.getLogger(PatronRequestWorkflow.class);
	private final PatronRequestResolutionStateTransition patronRequestResolutionStateTransition;

	public PatronRequestWorkflow(PatronRequestResolutionStateTransition patronRequestResolutionStateTransition) {
		this.patronRequestResolutionStateTransition = patronRequestResolutionStateTransition;
	}

	public Mono<PatronRequest> initiate(PatronRequest patronRequest) {
		log.debug("initializeRequestWorkflow({})", patronRequest);
		return Mono.just(patronRequest)
			.flatMap(patronRequestResolutionStateTransition::makeTransition);
	}
}
