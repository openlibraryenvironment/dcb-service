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
@Named("BorrowerRequestReturnTransit")
public class HandleBorrowerRequestReturnTransit implements WorkflowAction {
	private RequestWorkflowContextHelper requestWorkflowContextHelper;
	private PatronRequestRepository patronRequestRepository;

	public HandleBorrowerRequestReturnTransit(
		PatronRequestRepository patronRequestRepository,
		RequestWorkflowContextHelper requestWorkflowContextHelper) {
		this.patronRequestRepository = patronRequestRepository;
		this.requestWorkflowContextHelper = requestWorkflowContextHelper;
	}

	@Transactional
	public Mono<Map<String, Object>> execute(Map<String, Object> context) {
		StateChange sc = (StateChange) context.get("StateChange");
		log.debug("HandleBorrowerRequestReturnTransit {}", sc);

		PatronRequest pr = (PatronRequest) sc.getResource();
		if (pr != null) {
			pr.setLocalItemStatus(sc.getToState());
			pr.setStatus(PatronRequest.Status.RETURN_TRANSIT);
			log.debug("Set local status to RET-TRANSIT and save {}", pr);
			return Mono.from(patronRequestRepository.saveOrUpdate(pr))
				.thenReturn(context);
		} else {
			log.warn("Unable to locate patron request to mark as missing");
			return Mono.just(context);
		}
	}
}
