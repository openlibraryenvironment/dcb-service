package org.olf.dcb.request.workflow;

import io.micronaut.core.annotation.NonNull;
import org.olf.dcb.core.model.PatronRequest.Status;
import org.olf.dcb.request.fulfilment.RequestWorkflowContext;
import org.olf.dcb.statemodel.DCBGuardCondition;
import org.olf.dcb.statemodel.DCBTransitionResult;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Optional;

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

	// a way to avoid repeatedly attempting transitions
	// if the transition is applicable but the setting is disabled
	@NonNull
	default Mono<Boolean> isFunctionalSettingEnabled(RequestWorkflowContext ctx) {
		// Not all transitions have a setting
		return Mono.just(true);
	}
}
