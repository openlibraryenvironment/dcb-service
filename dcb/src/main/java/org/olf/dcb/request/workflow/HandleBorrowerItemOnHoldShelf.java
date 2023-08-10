package org.olf.dcb.request.workflow;

import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.request.fulfilment.RequestWorkflowContextHelper;
import org.olf.dcb.storage.PatronRequestRepository;
import org.olf.dcb.tracking.model.StateChange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import javax.transaction.Transactional;
import java.util.Map;
@Singleton
@Named("BorrowerRequestItemOnHoldShelf")
public class HandleBorrowerItemOnHoldShelf implements WorkflowAction {

        private static final Logger log = LoggerFactory.getLogger(HandleSupplierInTransit.class);
        private RequestWorkflowContextHelper requestWorkflowContextHelper;
        private PatronRequestRepository patronRequestRepository;

        public HandleBorrowerItemOnHoldShelf(
                PatronRequestRepository patronRequestRepository,
                RequestWorkflowContextHelper requestWorkflowContextHelper) {
                this.patronRequestRepository = patronRequestRepository;
                this.requestWorkflowContextHelper = requestWorkflowContextHelper;
        }
        
        @Transactional
        public Mono<Map<String,Object>> execute(Map<String,Object> context) {
                StateChange sc = (StateChange) context.get("StateChange");
                log.debug("HandleBorrowerItemOnHoldShelf {}",sc);
                PatronRequest pr = (PatronRequest) sc.getResource();
                if ( pr != null ) {
                        pr.setLocalItemStatus("ON_HOLD_SHELF");
                        log.debug("Set local status to ON_HOLD_SHELF and save {}",pr);
                        return Mono.from(patronRequestRepository.saveOrUpdate(pr))
                                .doOnNext(spr -> log.debug("Saved {}",spr))
                                .thenReturn(context);
                }
                else {
                        log.warn("Unable to locate patron request to mark hostlms item status to ON_HOLD_SHELF");
                        return Mono.just(context);
                }
        }
}
