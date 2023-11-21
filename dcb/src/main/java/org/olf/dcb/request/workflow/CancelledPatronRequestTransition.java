package org.olf.dcb.request.workflow;

import java.util.Optional;
import java.util.stream.Stream;

import org.olf.dcb.request.fulfilment.PatronRequestAuditService;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.PatronRequestAudit;
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
        private final PatronRequestAuditService patronRequestAuditService;

	
	public CancelledPatronRequestTransition(
                BeanProvider<PatronRequestWorkflowService> patronRequestWorkflowServiceProvider,
                PatronRequestAuditService patronRequestAuditService
                ) {
		this.patronRequestWorkflowServiceProvider = patronRequestWorkflowServiceProvider;
                this.patronRequestAuditService = patronRequestAuditService;
	}

	@Override
	public boolean isApplicableFor(PatronRequest pr) {
		return pr.getStatus() == Status.CANCELLED;
	}

	@Override
	public Mono<PatronRequest> attempt(PatronRequest patronRequest) {
                patronRequest.setStatus(Status.COMPLETED);
                return patronRequestAuditService.addAuditEntry(patronRequest, Status.CANCELLED, Status.COMPLETED)
                        .map(PatronRequestAudit::getPatronRequest);
	}

	@Override
	public Optional<Status> getTargetStatus() {
		return Optional.of(Status.COMPLETED);
	}
	
	@Override
	public boolean attemptAutomatically() {
		return true;
	}

}

