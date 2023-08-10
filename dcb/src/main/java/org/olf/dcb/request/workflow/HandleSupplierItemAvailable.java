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

import javax.transaction.Transactional;
import java.util.Map;
@Singleton
@Named("SupplierRequestItemAvailable")
public class HandleSupplierItemAvailable implements WorkflowAction {

	private static final Logger log = LoggerFactory.getLogger(HandleSupplierInTransit.class);
	private RequestWorkflowContextHelper requestWorkflowContextHelper;
	private PatronRequestRepository patronRequestRepository;
	private SupplierRequestRepository supplierRequestRepository;

	public HandleSupplierItemAvailable(
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
		log.debug("HandleSupplierItemAvailable {}",sc);
		SupplierRequest sr = (SupplierRequest) sc.getResource();
		return Mono.from(patronRequestRepository.findById(sr.getPatronRequest().getId()))
			.flatMap(pr -> {
				if (sr != null && pr != null) {
					sr.setLocalItemStatus("AVAILABLE");

					// An item becoming available means the request process has 'completed'
					pr.setStatus(PatronRequest.Status.FINALISED);

					log.debug("Set local status to AVAILABLE and save {}", sr);
					return Mono.from(supplierRequestRepository.saveOrUpdate(sr))
						.doOnNext(ssr -> log.debug("Saved {}", ssr))
						.flatMap(ssr -> Mono.from(patronRequestRepository.saveOrUpdate(pr)))
						.doOnNext(spr -> log.debug("Saved {}", spr))
						.thenReturn(context);
				} else {
					log.warn("Unable to locate supplier request to mark item as available");
					return Mono.just(context);
				}
			});


	}
}
