package org.olf.reshare.dcb.core;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.olf.reshare.dcb.core.interaction.HostLmsClient;
import org.olf.reshare.dcb.core.model.HostLms;
import org.olf.reshare.dcb.ingest.IngestSource;
import org.olf.reshare.dcb.ingest.IngestSourcesProvider;
import org.olf.reshare.dcb.storage.ProcessStateRepository;
import org.reactivestreams.Publisher;
import org.olf.reshare.dcb.core.model.ProcessState;

import io.micronaut.context.BeanContext;
import jakarta.inject.Singleton;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import  services.k_int.utils.UUIDUtils;


@Singleton
public class ProcessStateService {

        ProcessStateRepository processStateRepository;

	ProcessStateService(ProcessStateRepository processStateRepository) {
                this.processStateRepository = processStateRepository;
	}
	
        public Mono<ProcessState> updateState(UUID context, String processName, Map<String, Object> state) {

                UUID persistence_id = UUIDUtils.nameUUIDFromNamespaceAndString(context,processName);

                ProcessState ps = new ProcessState(persistence_id, context, processName, state);

               return Mono.from(processStateRepository.existsById(persistence_id))
                        .flatMap(exists -> Mono.fromDirect(exists ? processStateRepository.update(ps) : processStateRepository.save(ps)));
        }

        public Map<String, Object> getState(UUID context, String processName) {
                UUID persistence_id = UUIDUtils.nameUUIDFromNamespaceAndString(context,processName);

                ProcessState ps = Mono.from(processStateRepository.findById(persistence_id))
                        .block();

                return ps != null ? ps.getProcessState() : null;
        }
}
