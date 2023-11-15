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


@Singleton
@Named("BorrowerRequestItemReceived")
public class HandleBorrowerItemReceived implements WorkflowAction {

        private static final Logger log = LoggerFactory.getLogger(HandleBorrowerItemReceived.class);
        private RequestWorkflowContextHelper requestWorkflowContextHelper;
        private PatronRequestRepository patronRequestRepository;

        public HandleBorrowerItemReceived(
                PatronRequestRepository patronRequestRepository,
                RequestWorkflowContextHelper requestWorkflowContextHelper) {
                this.patronRequestRepository = patronRequestRepository;
                this.requestWorkflowContextHelper = requestWorkflowContextHelper;
        }
        
        @Transactional
        public Mono<Map<String,Object>> execute(Map<String,Object> context) {
                StateChange sc = (StateChange) context.get("StateChange");
                log.debug("HandleBorrowerItemReceived {}",sc);
                PatronRequest pr = (PatronRequest) sc.getResource();
                if ( pr != null ) {
                        pr.setLocalItemStatus(sc.getToState());
                        pr.setStatus(PatronRequest.Status.RECEIVED_AT_PICKUP);
                        log.debug("Set local status to RECEIVED and save {}",pr);
                        return Mono.from(patronRequestRepository.saveOrUpdate(pr))
                                .doOnNext(spr -> log.debug("Saved {}",spr))
                                .thenReturn(context);
                }
                else {
                        log.warn("Unable to locate patron request to mark as received");
                        return Mono.just(context);
                }
        }
}
