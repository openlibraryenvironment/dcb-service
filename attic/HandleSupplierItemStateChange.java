package org.olf.dcb.request.workflow;

import java.util.Map;

import org.olf.dcb.core.model.SupplierRequest;
import org.olf.dcb.storage.SupplierRequestRepository;
import org.olf.dcb.tracking.model.StateChange;

import jakarta.inject.Named;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Singleton
@Named("SupplierRequestItemStateChange")
public class HandleSupplierItemStateChange implements WorkflowAction {
	private final SupplierRequestRepository supplierRequestRepository;

	public HandleSupplierItemStateChange(SupplierRequestRepository supplierRequestRepository) {
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
