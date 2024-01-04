package org.olf.dcb.request.workflow;

import java.util.Optional;
import java.util.stream.Stream;

import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.PatronRequest.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micronaut.core.annotation.NonNull;
import reactor.core.publisher.Mono;

public class CleanupPatronRequestTransition implements PatronRequestStateTransition {
	
	private static final Logger log = LoggerFactory.getLogger(CleanupPatronRequestTransition.class);

	@Override
	public boolean isApplicableFor(PatronRequest pr) {
		Status s = pr.getStatus();

		return Stream.of(Status.ERROR, Status.CANCELLED)
			.anyMatch(s::equals);
	}

	@Override
	public Mono<PatronRequest> attempt(PatronRequest patronRequest) {

    log.info("CleanupPatronRequestTransition firing for {}",patronRequest);

		
		// Remove the hold at the Lender (LenderRequest)
		// - Remove virtual Patron if not associated with another request
		// -- And Remove DCB Patron Identity if the above was actioned. 
		// Remove the virtual item at the Borrower
		// Remove the virtual Bib at the Borrower
		// Remove the virtual 
		
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	@NonNull
	public Optional<Status> getTargetStatus() {
		return Optional.empty();
	}

	@Override
	public boolean attemptAutomatically() {
		return false;
	}
}
