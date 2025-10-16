package org.olf.dcb.request.workflow;

import static org.olf.dcb.core.model.PatronRequest.Status.CONFIRMED;
import static org.olf.dcb.core.model.PatronRequest.Status.HANDED_OFF_AS_LOCAL;

import java.util.List;
import java.util.Optional;

import org.olf.dcb.core.model.PatronRequest.Status;
import org.olf.dcb.request.fulfilment.RequestWorkflowContext;

import io.micronaut.context.annotation.Prototype;
import jakarta.inject.Named;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Prototype
@Named(HandleHandOffAsLocalRequest.NAME)
public class HandleHandOffAsLocalRequest implements PatronRequestStateTransition {
	
	protected static final String NAME = "HandOffAsLocalRequest";
	private static final List<Status> possibleSourceStatus = List.of(CONFIRMED);

	/**
	 * Attempts to transition the patron request to the next state,
	 * which is simply stating the request has been handed off to local workflows and DCB should no longer be involved.
	 *
	 * @param ctx the request context
	 * @return A Mono that emits the patron request after the transition, or an
	 *         error if the transition is not possible.
	 */
	@Override
	public Mono<RequestWorkflowContext> attempt(RequestWorkflowContext ctx) {
		
		return Mono.just(ctx)
			.filter( this::isApplicableFor ) // Extra caution. If the guard conditions fail then we can error (mono will be empty).
			.map( context -> {
				context.getPatronRequest().setStatus(HANDED_OFF_AS_LOCAL);
				context.getWorkflowMessages().add("Same Host/Agency request handed off as local transaction after hold placed with lender");

				log.info("Same Host/Agency request handed off as local only after hold placed with lender: {}", context.getPatronRequest());
				return context;
			})
			.doOnError(error -> {		
				log.error("Error occurred setting PatronRequest status to HANDED_OFF_AS_LOCAL: {}" , error.getMessage());
				ctx.getWorkflowMessages().add("Error occurred during handing off a patron request as a local only request: " + error.getMessage());
			})
			.switchIfEmpty( Mono.error(() ->
				new IllegalStateException("Attempted to Hand off Patron request as local from invalid state: " + ctx.getPatronRequest().toString()) ) );
	}

	@Override
	public boolean isApplicableFor(RequestWorkflowContext ctx) {
		final var pr = ctx.getPatronRequest();
		// Needs to be local flow and in a valid current state.
		return pr.isUsingLocalWorkflow() && getPossibleSourceStatus().contains(pr.getStatus());
	}
	
	@Override
	public List<Status> getPossibleSourceStatus() {
		return possibleSourceStatus;
	}
	
	@Override
	public Optional<Status> getTargetStatus() {
		return Optional.of(HANDED_OFF_AS_LOCAL);
	}

  @Override
  public String getName() {
    return NAME;
  }

  @Override
  public boolean attemptAutomatically() {
    return true;
  }
}
