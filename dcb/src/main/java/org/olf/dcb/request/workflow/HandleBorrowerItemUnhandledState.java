package org.olf.dcb.request.workflow;

import java.util.Map;

import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.storage.PatronRequestRepository;
import org.olf.dcb.tracking.model.StateChange;

import jakarta.inject.Named;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Singleton
@Named("BorrowerItemUnhandledState")
public class HandleBorrowerItemUnhandledState implements WorkflowAction {
	private final PatronRequestRepository patronRequestRepository;

	public HandleBorrowerItemUnhandledState(PatronRequestRepository patronRequestRepository) {
		this.patronRequestRepository = patronRequestRepository;
	}

	@Transactional
	public Mono<Map<String, Object>> execute(Map<String, Object> context) {
		StateChange sc = (StateChange) context.get("StateChange");

		log.warn("Unhandled BorrowerVirtualItem ToState: {}",
			sc.getToState());

		PatronRequest pr = (PatronRequest) sc.getResource();
		if (pr != null) {
			pr.setErrorMessage(
				"Virtual item status set to " + sc.getToState() + " (was " + sc.getFromState() + ") - Unhandled");

			// We don't take any special action, but we do record the fact that the locat item state has been updated
			pr.setLocalItemStatus(sc.getToState());

			return Mono.from(patronRequestRepository.saveOrUpdate(pr))
				.thenReturn(context);
		} else {
			log.warn("Unable to locate patron request for state change {}", sc);
			return Mono.just(context);
		}
	}
}
