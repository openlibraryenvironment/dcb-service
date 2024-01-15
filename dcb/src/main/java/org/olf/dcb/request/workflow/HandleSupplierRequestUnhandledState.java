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
@Named("SupplierRequestUnhandledState")

public class HandleSupplierRequestUnhandledState implements WorkflowAction {

        private static final Logger log = LoggerFactory.getLogger(HandleSupplierRequestUnhandledState.class);

        private SupplierRequestRepository supplierRequestRepository;
        public HandleSupplierRequestUnhandledState(SupplierRequestRepository supplierRequestRepository) {
                this.supplierRequestRepository = supplierRequestRepository;
        }

        @Transactional
        public Mono<Map<String,Object>> execute(Map<String,Object> context) {
                StateChange sc = (StateChange) context.get("StateChange");

								// We issue this as a warning... an unhandled state change.. we should add an explicit NoOp if there
								// really is no reaction
                log.warn("HandleSupplierRequestUnhandledState {}",sc);

                SupplierRequest sr = (SupplierRequest) sc.getResource();
                if ( sr != null ) {
                        sr.setLocalStatus(sc.getToState());
                        log.debug("Set local status save {}",sr);
                        return Mono.from(supplierRequestRepository.saveOrUpdate(sr))
                                .doOnNext(ssr -> log.debug("Saved {}",ssr))
                                .thenReturn(context);
                }
                else {
                        log.warn("Unable to locate supplier request");
                        return Mono.just(context);
                }
        }
}
