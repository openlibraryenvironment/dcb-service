package org.olf.dcb.request.workflow;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;
import java.util.Map;
import jakarta.inject.Singleton;
import jakarta.inject.Named;
import org.olf.dcb.tracking.model.StateChange;
import org.olf.dcb.core.model.SupplierRequest;
import org.olf.dcb.storage.SupplierRequestRepository;

@Singleton
@Named("SupplierRequestMissing")
public class HandleSupplierRequestMissing implements WorkflowAction {

        private static final Logger log = LoggerFactory.getLogger(HandleSupplierRequestMissing.class);
        private SupplierRequestRepository supplierRequestRepository;

        public HandleSupplierRequestMissing(SupplierRequestRepository supplierRequestRepository) {
                this.supplierRequestRepository = supplierRequestRepository;
        }

        public Mono<Map<String,Object>> execute(Map<String,Object> context) {
                StateChange sc = (StateChange) context.get("StateChange");
                log.debug("HandleSupplierRequestMissing {}",sc);

                SupplierRequest sr = (SupplierRequest) sc.getResource();
                if ( sr != null ) {
                        log.debug("Set local status to missing and save");
                        sr.setLocalStatus("MISSING");
                        return Mono.from(supplierRequestRepository.saveOrUpdate(sr))
                                .thenReturn(context);
                }
                else {
                        log.warn("Unable to locate supplier request to mark as missing");
                        return Mono.just(context);
                }
        }
}
