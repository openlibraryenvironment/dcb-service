package org.olf.dcb.request.workflow;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;
import java.util.Map;
import jakarta.inject.Singleton;
import jakarta.inject.Named;
import org.olf.dcb.tracking.model.StateChange;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.storage.PatronRequestRepository;
import jakarta.transaction.Transactional;

import org.olf.dcb.request.fulfilment.RequestWorkflowContext;
import org.olf.dcb.request.fulfilment.RequestWorkflowContextHelper;
import java.util.UUID;
import org.olf.dcb.core.HostLmsService;

import org.olf.dcb.core.interaction.HostLmsClient;


// We have detected that the borrower system state has indeed been updated to TRANSIT.. this is a reaction
// to the call made by updatePatronItem in HandleSupplierInTransit and allows us to close the loop
@Singleton
@Named("BorrowerRequestItemInTransit")
public class HandleBorrowerItemInTransit implements WorkflowAction {

        private static final Logger log = LoggerFactory.getLogger(HandleBorrowerItemInTransit.class);
        private RequestWorkflowContextHelper requestWorkflowContextHelper;
        private PatronRequestRepository patronRequestRepository;

        public HandleBorrowerItemInTransit(
                PatronRequestRepository patronRequestRepository,
                RequestWorkflowContextHelper requestWorkflowContextHelper) {
                this.patronRequestRepository = patronRequestRepository;
                this.requestWorkflowContextHelper = requestWorkflowContextHelper;
        }
        
        @Transactional
        public Mono<Map<String,Object>> execute(Map<String,Object> context) {
                StateChange sc = (StateChange) context.get("StateChange");
                log.debug("HandleBorrowerItemInTransit {}",sc);
                PatronRequest pr = (PatronRequest) sc.getResource();
                if ( pr != null ) {
                        pr.setLocalItemStatus("TRANSIT");
                        pr.setStatus(PatronRequest.Status.PICKUP_TRANSIT);
                        log.debug("Set local status to TRANSIT and save {}",pr);
                        return Mono.from(patronRequestRepository.saveOrUpdate(pr))
                                .doOnNext(spr -> log.debug("Saved {}",spr))
                                .thenReturn(context);
                }
                else {
                        log.warn("Unable to locate patron request to mark as missing");
                        return Mono.just(context);
                }
        }
}
