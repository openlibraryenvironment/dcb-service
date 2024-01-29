package org.olf.dcb.request.workflow;

import java.util.Map;

import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.request.fulfilment.RequestWorkflowContextHelper;
import org.olf.dcb.storage.PatronRequestRepository;
import org.olf.dcb.tracking.model.StateChange;

import jakarta.inject.Named;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Singleton
@Named("BorrowerRequestItemMissing")
public class HandleBorrowerItemMissing implements WorkflowAction {
	private RequestWorkflowContextHelper requestWorkflowContextHelper;
	private PatronRequestRepository patronRequestRepository;

	public HandleBorrowerItemMissing(
		PatronRequestRepository patronRequestRepository,
		RequestWorkflowContextHelper requestWorkflowContextHelper) {
		this.patronRequestRepository = patronRequestRepository;
		this.requestWorkflowContextHelper = requestWorkflowContextHelper;
	}

	@Transactional
	public Mono<Map<String, Object>> execute(Map<String, Object> context) {
		StateChange sc = (StateChange) context.get("StateChange");
		log.debug("HandleBorrowerItemMissing {}", sc);
		PatronRequest pr = (PatronRequest) sc.getResource();
		if (pr != null) {
			pr.setLocalItemStatus(sc.getToState());
			return Mono.from(
				patronRequestRepository.saveOrUpdate(pr)).thenReturn(
				context);
		} else {
			return Mono.just(context);
		}
	}
}
