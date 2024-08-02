package org.olf.dcb.request.workflow;

import io.micronaut.context.annotation.Prototype;
import io.micronaut.core.annotation.NonNull;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.PatronRequest.Status;
import org.olf.dcb.request.fulfilment.PatronRequestAuditService;
import org.olf.dcb.request.fulfilment.RequestWorkflowContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Optional;

@Prototype
public class CleanupPatronRequestTransition implements PatronRequestStateTransition {
	
	private static final Logger log = LoggerFactory.getLogger(CleanupPatronRequestTransition.class);

	private static final List<Status> possibleSourceStatus = List.of(Status.ERROR);
	
  private final PatronRequestAuditService patronRequestAuditService;

  public CleanupPatronRequestTransition(
    PatronRequestAuditService patronRequestAuditService
    ) {
    this.patronRequestAuditService = patronRequestAuditService;
  }

	@Override
	public boolean isApplicableFor(RequestWorkflowContext ctx) {
		return getPossibleSourceStatus().contains(ctx.getPatronRequest().getStatus());
	}

	@Override
	public Mono<RequestWorkflowContext> attempt(RequestWorkflowContext ctx) {

		PatronRequest patronRequest = ctx.getPatronRequest();
    log.info("CleanupPatronRequestTransition firing for {}",patronRequest);

		// Setting the status to completed should cause the cleanup routine to fire which will do all the work we need to FINALISE the request
    Status old_state = patronRequest.getStatus();
    patronRequest.setStatus(Status.COMPLETED);
    return patronRequestAuditService.addAuditEntry(patronRequest, "Manual cleanup actioned.")
      .thenReturn(ctx);
	}

	@Override
	public List<Status> getPossibleSourceStatus() {
		return possibleSourceStatus;
	}
	
	@Override
	@NonNull
	public Optional<Status> getTargetStatus() {
		return Optional.of(Status.COMPLETED);
	}

	@Override
	public boolean attemptAutomatically() {
		return false;
	}

  @Override     
  public String getName() {
    return "CleanupPatronRequestTransition";
  }

}
