package org.olf.dcb.request.workflow;

import java.util.Optional;
import java.util.stream.Stream;

import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.PatronRequest.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micronaut.core.annotation.NonNull;
import reactor.core.publisher.Mono;

import org.olf.dcb.request.fulfilment.PatronRequestAuditService;

import io.micronaut.context.BeanProvider;
import io.micronaut.context.annotation.Prototype;
import reactor.core.publisher.Mono;

@Prototype
public class CleanupPatronRequestTransition implements PatronRequestStateTransition {
	
	private static final Logger log = LoggerFactory.getLogger(CleanupPatronRequestTransition.class);

  private final PatronRequestAuditService patronRequestAuditService;

  public CleanupPatronRequestTransition(
    PatronRequestAuditService patronRequestAuditService
    ) {
    this.patronRequestAuditService = patronRequestAuditService;
  }


	@Override
	public boolean isApplicableFor(PatronRequest pr) {
		Status s = pr.getStatus();

		return Stream.of(Status.ERROR, Status.CANCELLED)
			.anyMatch(s::equals);
	}

	@Override
	public Mono<PatronRequest> attempt(PatronRequest patronRequest) {

    log.info("CleanupPatronRequestTransition firing for {}",patronRequest);

		// Setting the status to completed should cause the cleanup routine to fire which will do all the work we need to FINALISE the request
    Status old_state = patronRequest.getStatus();
    patronRequest.setStatus(Status.COMPLETED);
    return patronRequestAuditService.addAuditEntry(patronRequest, old_state, Status.COMPLETED)
      .thenReturn(patronRequest);
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
}
