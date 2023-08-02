package org.olf.dcb.request.workflow;

import java.util.Optional;

import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.PatronRequest.Status;

import io.micronaut.core.annotation.NonNull;
import reactor.core.publisher.Mono;

public interface PatronRequestStateTransition {
	
	boolean isApplicableFor(PatronRequest pr);

	@NonNull
	Mono<PatronRequest> attempt(PatronRequest patronRequest);
	
	@NonNull
	Optional<Status> getTargetStatus();
	
	public default boolean attemptAutomatically() {
		return true;
	}
}
