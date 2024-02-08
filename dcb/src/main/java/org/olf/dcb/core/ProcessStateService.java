package org.olf.dcb.core;

import java.util.Map;
import java.util.UUID;

import org.olf.dcb.core.model.ProcessState;
import org.olf.dcb.storage.ProcessStateRepository;

import io.micronaut.transaction.TransactionDefinition.Propagation;
import io.micronaut.transaction.annotation.Transactional;
import jakarta.inject.Singleton;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;
import services.k_int.utils.UUIDUtils;


@Singleton
public class ProcessStateService {

	ProcessStateRepository processStateRepository;

	ProcessStateService(ProcessStateRepository processStateRepository) {
		this.processStateRepository = processStateRepository;
	}

	@Transactional
	public Mono<ProcessState> updateState(UUID context, String processName, Map<String, Object> state) {

		UUID persistence_id = UUIDUtils.nameUUIDFromNamespaceAndString(context, processName);

		ProcessState ps = new ProcessState(persistence_id, context, processName, state);

		return Mono.from(processStateRepository.existsById(persistence_id))
			.flatMap(exists -> Mono.fromDirect(exists ? processStateRepository.update(ps) : processStateRepository.save(ps)));
	}

	@Transactional
	public Mono<ProcessState> getState(UUID context, String processName) {
		UUID persistence_id = UUIDUtils.nameUUIDFromNamespaceAndString(context, processName);

		return Mono.from(processStateRepository.findById(persistence_id));
	}

	/**
	 * getStateMap returns a map only in preparation for a hazelcast proxy layer
	 * where the repository only serves as the backing store for the distributed
	 * map.
	 */
	public Mono<Map<String, Object>> getStateMap(UUID context, String processName) {
		return getState(context, processName).map(ps -> ps.getProcessState());
	}
}
