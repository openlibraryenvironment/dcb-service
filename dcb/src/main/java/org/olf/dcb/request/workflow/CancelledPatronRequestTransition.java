package org.olf.dcb.request.workflow;

import java.util.Optional;
import java.util.stream.Stream;

import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.PatronRequest.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micronaut.context.BeanProvider;
import io.micronaut.context.annotation.Prototype;
import reactor.core.publisher.Mono;

@Prototype
public class CancelledPatronRequestTransition implements PatronRequestStateTransition {
	private static final Logger log = LoggerFactory.getLogger(CancelledPatronRequestTransition.class);

	// Provider to prevent circular reference exception by allowing lazy access to
	// this singleton.
	private final BeanProvider<PatronRequestWorkflowService> patronRequestWorkflowServiceProvider;
	
	public CancelledPatronRequestTransition(BeanProvider<PatronRequestWorkflowService> patronRequestWorkflowServiceProvider) {
		this.patronRequestWorkflowServiceProvider = patronRequestWorkflowServiceProvider;
	}

	@Override
	public boolean isApplicableFor(PatronRequest pr) {
		return pr.getStatus() != Status.ERROR;
	}

	@Override
	public Mono<PatronRequest> attempt(PatronRequest patronRequest) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Optional<Status> getTargetStatus() {
		return Optional.of(Status.CANCELLED);
	}

}
