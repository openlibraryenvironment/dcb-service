package org.olf.dcb.request.workflow;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.olf.dcb.request.fulfilment.PatronRequestAuditService;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.PatronRequestAudit;
import org.olf.dcb.core.model.PatronRequest.Status;
import org.olf.dcb.request.fulfilment.RequestWorkflowContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micronaut.context.BeanProvider;
import io.micronaut.context.annotation.Prototype;
import reactor.core.publisher.Mono;

@Prototype
public class CancelledPatronRequestTransition implements PatronRequestStateTransition {
	private static final Logger log = LoggerFactory.getLogger(CancelledPatronRequestTransition.class);

	private static final List<Status> possibleSourceStatus = List.of(Status.CANCELLED);
	
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
	public boolean isApplicableFor(RequestWorkflowContext ctx) {
		return getPossibleSourceStatus().contains(ctx.getPatronRequest().getStatus());
	}

	@Override
	public Mono<RequestWorkflowContext> attempt(RequestWorkflowContext ctx) {
		log.info("CancelledPatronRequestTransition firing for {}",ctx.getPatronRequest());
		PatronRequest patronRequest = ctx.getPatronRequest();
		Status old_state = patronRequest.getStatus();
    patronRequest.setStatus(Status.COMPLETED);
    return patronRequestAuditService.addAuditEntry(patronRequest, old_state, Status.COMPLETED)
      .thenReturn(ctx);
	}

	@Override
	public List<Status> getPossibleSourceStatus() {
		return possibleSourceStatus;
	}
	
	@Override
	public Optional<Status> getTargetStatus() {
		return Optional.of(Status.COMPLETED);
	}
	
	@Override
	public boolean attemptAutomatically() {
		return true;
	}

	@Override
	public String getName() {
		return "CancelledPatronRequestTransition";
	}
}

