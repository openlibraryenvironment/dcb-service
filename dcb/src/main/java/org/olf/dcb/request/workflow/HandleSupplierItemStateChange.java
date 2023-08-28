package org.olf.dcb.request.workflow;

import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.SupplierRequest;
import org.olf.dcb.request.fulfilment.RequestWorkflowContextHelper;
import org.olf.dcb.storage.PatronRequestRepository;
import org.olf.dcb.storage.SupplierRequestRepository;
import org.olf.dcb.tracking.model.StateChange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;


// This handler is called just to update the state of the supplier item - its a noop other than
// adjusting the local copy of the state at the supplier
import jakarta.transaction.Transactional;
import java.util.Map;
@Singleton
@Named("SupplierRequestItemStateChange")
public class HandleSupplierItemStateChange implements WorkflowAction {

	private static final Logger log = LoggerFactory.getLogger(HandleSupplierInTransit.class);
	private RequestWorkflowContextHelper requestWorkflowContextHelper;
	private PatronRequestRepository patronRequestRepository;
	private SupplierRequestRepository supplierRequestRepository;

	public HandleSupplierItemStateChange(
		RequestWorkflowContextHelper requestWorkflowContextHelper,
		PatronRequestRepository patronRequestRepository,
		SupplierRequestRepository supplierRequestRepository)
	{
		this.requestWorkflowContextHelper = requestWorkflowContextHelper;
		this.patronRequestRepository = patronRequestRepository;
		this.supplierRequestRepository = supplierRequestRepository;
	}


	@Transactional
	public Mono<Map<String,Object>> execute(Map<String,Object> context) {
		StateChange sc = (StateChange) context.get("StateChange");
		log.debug("HandleSupplierItemStateChange {}",sc);
		SupplierRequest sr = (SupplierRequest) sc.getResource();
		sr.setLocalItemStatus(sc.getToState());
		return Mono.from(supplierRequestRepository.saveOrUpdate(sr))
			.thenReturn(context);
	}
}
