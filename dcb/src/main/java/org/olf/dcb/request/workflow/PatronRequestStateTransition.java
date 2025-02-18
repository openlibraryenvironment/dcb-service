package org.olf.dcb.request.workflow;

import java.util.List;
import java.util.Optional;

import org.olf.dcb.core.model.PatronRequest.Status;
import org.olf.dcb.request.fulfilment.RequestWorkflowContext;
import org.olf.dcb.statemodel.DCBGuardCondition;
import org.olf.dcb.statemodel.DCBTransitionResult;

import io.micronaut.core.annotation.NonNull;
import reactor.core.publisher.Mono;

public interface PatronRequestStateTransition {
	
	boolean isApplicableFor(RequestWorkflowContext ctx);

	@NonNull
	Mono<RequestWorkflowContext> attempt(RequestWorkflowContext ctx);

	@NonNull
	Optional<Status> getTargetStatus();

	@NonNull
	List<Status> getPossibleSourceStatus();

	// Lets us know if this method is one the system should apply automatically OR
	// if it's intended to be a user triggered action
	boolean attemptAutomatically();

	@NonNull
	String getName();

	@NonNull
	default List<DCBGuardCondition> getGuardConditions() {
		return List.of();
	}

	@NonNull
	default List<DCBTransitionResult> getOutcomes() {
		return List.of();
	}
}
