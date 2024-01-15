package org.olf.dcb.request.workflow;

import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.olf.dcb.core.model.SupplierRequest;
import org.olf.dcb.storage.SupplierRequestRepository;
import org.olf.dcb.tracking.model.StateChange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import jakarta.transaction.Transactional;
import java.util.Map;

@Singleton
@Named("SupplierRequestCancelled")
public class HandleSupplierRequestCancelled implements WorkflowAction {

        private static final Logger log = LoggerFactory.getLogger(HandleSupplierRequestCancelled.class);

        private SupplierRequestRepository supplierRequestRepository;

        public HandleSupplierRequestCancelled(SupplierRequestRepository supplierRequestRepository) {
                this.supplierRequestRepository = supplierRequestRepository;
        }

        @Transactional
        public Mono<Map<String,Object>> execute(Map<String,Object> context) {
                StateChange sc = (StateChange) context.get("StateChange");
                log.debug("HandleSupplierRequestCancelled {}",sc);

                SupplierRequest sr = (SupplierRequest) sc.getResource();
                if ( sr != null ) {
                        sr.setLocalStatus("CANCELLED");
                        log.debug("Set local status to placed and save {}",sr);
                        return Mono.from(supplierRequestRepository.saveOrUpdate(sr))
                                .doOnNext(ssr -> log.debug("Saved {}",ssr))
                                .thenReturn(context);
                }
                else {
                        log.warn("Unable to locate supplier request to mark hostlms hold to placed");
                        return Mono.just(context);
                }
        }
}
