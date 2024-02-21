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
@Named("SupplierRequestUnhandledState")
public class HandleSupplierRequestUnhandledState implements WorkflowAction {
	private final SupplierRequestRepository supplierRequestRepository;

	public HandleSupplierRequestUnhandledState(SupplierRequestRepository supplierRequestRepository) {
		this.supplierRequestRepository = supplierRequestRepository;
	}

	@Transactional
	public Mono<Map<String, Object>> execute(Map<String, Object> context) {
		final var sc = (StateChange) context.get("StateChange");

		// We issue this as a warning... an unhandled state change.. we should add an explicit NoOp if there
		// really is no reaction
		log.warn("HandleSupplierRequestUnhandledState {}", sc);

		final var sr = (SupplierRequest) sc.getResource();

		if (sr != null) {
			sr.setLocalStatus(sc.getToState());
			log.debug("Set local status save {}", sr);
			return Mono.from(
					supplierRequestRepository.saveOrUpdate(sr))
				.doOnNext(ssr -> log.debug("Saved {}", ssr))
				.thenReturn(context);
		} else {
			log.warn("Unable to locate supplier request");
			return Mono.just(context);
		}
	}
}
