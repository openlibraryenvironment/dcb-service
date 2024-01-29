package org.olf.dcb.request.workflow;

import java.util.Map;

import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.request.fulfilment.RequestWorkflowContextHelper;
import org.olf.dcb.storage.PatronRequestRepository;
import org.olf.dcb.tracking.model.StateChange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.inject.Named;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;
import reactor.core.publisher.Mono;


// We have detected that the borrower system state has indeed been updated to TRANSIT.. this is a reaction
// to the call made by updatePatronItem in HandleSupplierInTransit and allows us to close the loop
@Singleton
@Named("BorrowerRequestItemInTransit")
public class HandleBorrowerItemInTransit implements WorkflowAction {
	private static final Logger log = LoggerFactory.getLogger(
		HandleBorrowerItemInTransit.class);
	private RequestWorkflowContextHelper requestWorkflowContextHelper;
	private PatronRequestRepository patronRequestRepository;

	public HandleBorrowerItemInTransit(
		PatronRequestRepository patronRequestRepository,
		RequestWorkflowContextHelper requestWorkflowContextHelper) {
		this.patronRequestRepository = patronRequestRepository;
		this.requestWorkflowContextHelper = requestWorkflowContextHelper;
	}

	@Transactional
	public Mono<Map<String, Object>> execute(Map<String, Object> context) {
		StateChange sc = (StateChange) context.get("StateChange");
		log.debug("HandleBorrowerItemInTransit {}", sc);
		PatronRequest pr = (PatronRequest) sc.getResource();
		if (pr != null) {
			pr.setLocalItemStatus(sc.getToState());
			pr.setStatus(PatronRequest.Status.PICKUP_TRANSIT);
			log.debug("Set local status to TRANSIT and save {}", pr);
			return Mono.from(patronRequestRepository.saveOrUpdate(pr))
				.doOnNext(spr -> log.debug("Saved {}", spr))
				.doOnError(
					error -> log.error("Error occurred in handle item in transit: ",
						error))
				.thenReturn(context);
		} else {
			log.warn("Unable to locate patron request to mark as missing");
			return Mono.just(context);
		}
	}
}
