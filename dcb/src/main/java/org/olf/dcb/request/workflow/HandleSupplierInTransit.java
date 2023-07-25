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
@Named("SupplierRequestInTransit")
public class HandleSupplierInTransit implements WorkflowAction {

        private static final Logger log = LoggerFactory.getLogger(HandleSupplierInTransit.class);
        private SupplierRequestRepository supplierRequestRepository;

        public HandleSupplierInTransit(SupplierRequestRepository supplierRequestRepository) {
                this.supplierRequestRepository = supplierRequestRepository;
        }

        public Mono<Map<String,Object>> execute(Map<String,Object> context) {
                StateChange sc = (StateChange) context.get("StateChange");
                log.debug("HandleSupplierInTransit {}",sc);

                SupplierRequest sr = (SupplierRequest) sc.getResource();
                if ( sr != null ) {
                        sr.setLocalStatus("TRANSIT");
                        return Mono.from(supplierRequestRepository.saveOrUpdate(sr))
                                .thenReturn(context);
                }
                else {
                        return Mono.just(context);
                }
        }
}
