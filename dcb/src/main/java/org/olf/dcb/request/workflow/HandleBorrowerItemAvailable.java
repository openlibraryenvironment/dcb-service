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
import javax.transaction.Transactional;
import org.olf.dcb.request.fulfilment.RequestWorkflowContext;
import org.olf.dcb.request.fulfilment.RequestWorkflowContextHelper;
import java.util.UUID;
import org.olf.dcb.core.HostLmsService;

import org.olf.dcb.core.interaction.HostLmsClient;


@Singleton
@Named("BorrowerRequestItemAvailable")
public class HandleBorrowerItemAvailable implements WorkflowAction {

        private static final Logger log = LoggerFactory.getLogger(HandleBorrowerItemAvailable.class);
        private RequestWorkflowContextHelper requestWorkflowContextHelper;
        private PatronRequestRepository patronRequestRepository;
        private HostLmsService hostLmsService;



        public HandleBorrowerItemAvailable(
                PatronRequestRepository patronRequestRepository,
                HostLmsService hostLmsService,
                RequestWorkflowContextHelper requestWorkflowContextHelper) {
                this.patronRequestRepository = patronRequestRepository;
                this.hostLmsService = hostLmsService;
                this.requestWorkflowContextHelper = requestWorkflowContextHelper;
        }
        
        @Transactional
        public Mono<Map<String,Object>> execute(Map<String,Object> context) {
                StateChange sc = (StateChange) context.get("StateChange");
                log.debug("HandleBorrowerItemAvailable {}",sc);
                PatronRequest pr = (PatronRequest) sc.getResource();
                if ( pr != null ) {
                        pr.setLocalItemStatus("AVAILABLE");

                        return requestWorkflowContextHelper.fromPatronRequest(pr)
                                // For now we have decided we don't want to set ItemStatus to OFF CAMPUS preferring
                                // to wait for checkout to patron
                                // .flatMap( this::updateSupplyingSystems )
                                .flatMap(rwc -> Mono.from(patronRequestRepository.saveOrUpdate(pr)))
                                .doOnNext(spr -> log.debug("Saved {}",spr))
                                .thenReturn(context);
                }
                else {
                        log.warn("Unable to locate patron request to mark as available");
                        return Mono.just(context);
                }
        }


        public Mono<RequestWorkflowContext> updateSupplyingSystems(RequestWorkflowContext rwc) {
                PatronRequest pr = rwc.getPatronRequest();
                // If we are in RET-STD workflow, an item becomming available at the borrowing agency is an indication that it is
                // Checked in, and we should update the status of the item at the lending institution.
                if ( pr.getActiveWorkflow() != null ) {
                        if ( pr.getActiveWorkflow().equals("RET-STD") ) {
                                // Standard workflow - remote brrower, patron picking up from a library in the home system
                                log.info("RET-STD:: We should tell the lender system that the item has arrived");
                                return setHomeItemOffCampus(rwc);
                        }
                        else {
                                log.warn("Unhandled workflow {}",pr.getActiveWorkflow());
                        }
                }
                else {
                        log.warn("Request has no active workflow, just store state");
                }
                return Mono.just(rwc);
        }

        public Mono<RequestWorkflowContext> setHomeItemOffCampus(RequestWorkflowContext rwc) {
                if ( ( rwc.getSupplierRequest() != null ) && 
                     ( rwc.getSupplierRequest().getLocalItemId() != null ) &&
                     ( rwc.getLenderSystemCode() != null ) ) {
                        log.debug("Update supplying system item: {}",rwc.getSupplierRequest().getLocalItemId());
                        return hostLmsService.getClientFor(rwc.getLenderSystemCode())
                                .flatMap(hostLmsClient -> hostLmsClient.updateItemStatus(rwc.getSupplierRequest().getLocalItemId(), HostLmsClient.CanonicalItemState.OFFSITE))
                                .thenReturn(rwc);
                }
                else { 
                        log.error("Missing data attempting to set home item off campus {}",rwc);
                        return Mono.just(rwc);
                }       
        }

}
