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
import org.olf.dcb.request.fulfilment.RequestWorkflowContext;
import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.interaction.HostLmsClient.CanonicalItemState;


import jakarta.transaction.Transactional;
import java.util.Map;
@Singleton
@Named("BorrowerRequestItemOnHoldShelf")
public class HandleBorrowerItemOnHoldShelf implements WorkflowAction {

        private static final Logger log = LoggerFactory.getLogger(HandleSupplierInTransit.class);
        private RequestWorkflowContextHelper requestWorkflowContextHelper;
        private PatronRequestRepository patronRequestRepository;
        private HostLmsService hostLmsService;

        public HandleBorrowerItemOnHoldShelf(
                PatronRequestRepository patronRequestRepository,
		HostLmsService hostLmsService,
                RequestWorkflowContextHelper requestWorkflowContextHelper) {
                this.patronRequestRepository = patronRequestRepository;
                this.requestWorkflowContextHelper = requestWorkflowContextHelper;
                this.hostLmsService = hostLmsService;
        }
        
        @Transactional
        public Mono<Map<String,Object>> execute(Map<String,Object> context) {
                StateChange sc = (StateChange) context.get("StateChange");
                log.info("HandleBorrowerItemOnHoldShelf {}",sc);
                PatronRequest pr = (PatronRequest) sc.getResource();
                if ( pr != null ) {
                        pr.setStatus(PatronRequest.Status.READY_FOR_PICKUP);
                        pr.setLocalItemStatus(sc.getToState());
                        log.debug("Set local status to READY_FOR_PICKUP and save {}",pr);
                        return requestWorkflowContextHelper.fromPatronRequest(pr)
                                .flatMap( this::updateSupplierItemToReceived )
                                .flatMap(rwc -> Mono.from(patronRequestRepository.saveOrUpdate(pr)))
                                .doOnNext(spr -> log.debug("Saved {}",spr))
				.doOnError(error -> log.error("Error occurred in handle item on hold shelf: ", error))
                                .thenReturn(context);

                }
                else {
                        log.warn("Unable to locate patron request to mark hostlms item status to ON_HOLD_SHELF");
                        return Mono.just(context);
                }
        }

        public Mono<RequestWorkflowContext> updateSupplierItemToReceived(RequestWorkflowContext rwc) {
                if ( ( rwc.getSupplierRequest() != null ) &&
                     ( rwc.getSupplierRequest().getLocalItemId() != null ) &&
                     ( rwc.getLenderSystemCode() != null ) ) {

									final var supplierRequest = rwc.getSupplierRequest();

			return hostLmsService.getClientFor(rwc.getLenderSystemCode())
                                // updateItemStatus here should be clearing the m-flag (Message)
                		.flatMap( hostLmsClient ->  hostLmsClient.updateItemStatus(supplierRequest.getLocalItemId(),
											CanonicalItemState.RECEIVED, supplierRequest.getLocalId()))
                                .thenReturn(rwc);
		}

		return Mono.just(rwc);
	}
}
