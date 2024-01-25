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
@Named("SupplierRequestPlaced")
public class HandleSupplierRequestPlaced implements WorkflowAction {
	private final SupplierRequestRepository supplierRequestRepository;

	public HandleSupplierRequestPlaced(SupplierRequestRepository supplierRequestRepository) {
		this.supplierRequestRepository = supplierRequestRepository;
	}

	@Transactional
	public Mono<Map<String, Object>> execute(Map<String, Object> context) {
		StateChange sc = (StateChange) context.get("StateChange");
		log.debug("HandleSupplierRequestPlaced {}", sc);

		SupplierRequest sr = (SupplierRequest) sc.getResource();
		if (sr != null) {
			sr.setLocalStatus("PLACED");
			log.debug("Set local status to placed and save {}", sr);
			return Mono.from(supplierRequestRepository.saveOrUpdate(sr))
				.doOnNext(ssr -> log.debug("Saved {}", ssr))
				.thenReturn(context);
		} else {
			log.warn("Unable to locate supplier request to mark hostlms hold to placed");
			return Mono.just(context);
		}
	}
}
