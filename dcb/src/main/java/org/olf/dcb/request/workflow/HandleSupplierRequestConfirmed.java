package org.olf.dcb.request.workflow;

import static org.olf.dcb.core.interaction.HostLmsRequest.HOLD_CONFIRMED;

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
@Named("SupplierRequestConfirmed")
public class HandleSupplierRequestConfirmed implements WorkflowAction {
	private final SupplierRequestRepository supplierRequestRepository;

	public HandleSupplierRequestConfirmed(SupplierRequestRepository supplierRequestRepository) {
		this.supplierRequestRepository = supplierRequestRepository;
	}

	@Transactional
	public Mono<Map<String, Object>> execute(Map<String, Object> context) {
		if (context.get("StateChange") instanceof StateChange stateChange) {
			log.debug("HandleSupplierRequestConfirmed {}", stateChange);

			if (stateChange.getResource() instanceof SupplierRequest supplierRequest) {
				supplierRequest.setLocalStatus(HOLD_CONFIRMED);

				log.debug("Set local status to placed and save {}", supplierRequest);

				return Mono.from(supplierRequestRepository.saveOrUpdate(supplierRequest))
					.doOnNext(savedSupplierRequest -> log.debug("Saved {}", savedSupplierRequest))
					.thenReturn(context);
			} else {
				log.warn("Unable to locate supplier request to mark local status as confirmed");

				return Mono.just(context);
			}
		}
		else {
			log.error("State change from context is incorrect type: {}", context);

			return Mono.error(new RuntimeException("State change from context is incorrect type"));
		}
	}
}
