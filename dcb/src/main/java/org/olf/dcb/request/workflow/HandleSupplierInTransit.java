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
import org.olf.dcb.core.model.SupplierRequest;
import org.olf.dcb.storage.SupplierRequestRepository;
import org.olf.dcb.storage.PatronRequestRepository;
import javax.transaction.Transactional;

import org.olf.dcb.request.fulfilment.RequestWorkflowContext;
import org.olf.dcb.request.fulfilment.RequestWorkflowContextHelper;
import java.util.UUID;
import org.olf.dcb.core.HostLmsService;

import org.olf.dcb.core.interaction.HostLmsClient;


@Singleton
@Named("SupplierRequestInTransit")
public class HandleSupplierInTransit implements WorkflowAction {

        private static final Logger log = LoggerFactory.getLogger(HandleSupplierInTransit.class);
        private SupplierRequestRepository supplierRequestRepository;
        private PatronRequestRepository patronRequestRepository;
        private RequestWorkflowContextHelper requestWorkflowContextHelper;
	private HostLmsService hostLmsService;

        public HandleSupplierInTransit(
                SupplierRequestRepository supplierRequestRepository,
                PatronRequestRepository patronRequestRepository,
		HostLmsService hostLmsService,
                RequestWorkflowContextHelper requestWorkflowContextHelper) {
                this.supplierRequestRepository = supplierRequestRepository;
                this.patronRequestRepository = patronRequestRepository;
                this.requestWorkflowContextHelper = requestWorkflowContextHelper;
                this.hostLmsService = hostLmsService;
        }
        
        @Transactional
        public Mono<Map<String,Object>> execute(Map<String,Object> context) {
                StateChange sc = (StateChange) context.get("StateChange");
                log.debug("HandleSupplierInTransit {}",sc);

                SupplierRequest sr = (SupplierRequest) sc.getResource();
                if ( sr != null ) {
                        sr.setLocalStatus("TRANSIT");
                        log.debug("Setting local status to TRANSIT and saving...{}",sr);
                        return requestWorkflowContextHelper.fromSupplierRequest(sr)
                                .flatMap( this::updateUpstreamSystems )
                                // If we managed to update other systems, then update the supplier request
				// This will cause st.setLocalStatus("TRANSIT") above to be saved and mean our local state is aligned with the supplier req
                                .flatMap( this::saveSupplierRequest )
                                .flatMap( this::updatePatronRequest )
                                .thenReturn(context);
                }
                else {
			log.warn("Supplier request in context was null. Cannot save");
                        return Mono.just(context);
                }
        }

        public Mono<RequestWorkflowContext> saveSupplierRequest(RequestWorkflowContext rwc) {
		return Mono.from(supplierRequestRepository.saveOrUpdate(rwc.getSupplierRequest()))
			.thenReturn(rwc);
	}

        public Mono<RequestWorkflowContext> updatePatronRequest(RequestWorkflowContext rwc) {
		PatronRequest pr = rwc.getPatronRequest();
		pr.setStatus(PatronRequest.Status.PICKUP_TRANSIT);
		return Mono.from(patronRequestRepository.saveOrUpdate(pr))
			.thenReturn(rwc);
	}


        // If there is a separate pickup location, the pickup location needs to be updated
        // If there is a separate patron request (There always will be EXCEPT for the "Local" case) update it
        public Mono<RequestWorkflowContext> updateUpstreamSystems(RequestWorkflowContext rwc) {
                log.debug("updateUpstreamSystems rwc={},{}",rwc.getPatronSystemCode(),rwc.getPatronRequest().getPickupRequestId());

		return updatePatronItem(rwc)
			.flatMap(this::updatePickupItem);

		// rwc.getPatronRequest().getLocalRequestId() == The request placed at the patron home system to represent this loan
		// rwc.getPatronRequest().getPickupRequestId() == The request placed at a third party pickup location
        }

	public Mono<RequestWorkflowContext> updatePatronItem(RequestWorkflowContext rwc) {
		if ( rwc.getPatronRequest().getLocalItemId() != null ) {
			log.debug("Update patron system item: {}",rwc.getPatronRequest().getLocalItemId());
			return hostLmsService.getClientFor(rwc.getPatronSystemCode())
		 		.flatMap(hostLmsClient -> hostLmsClient.updateItemStatus(rwc.getPatronRequest().getLocalItemId(), HostLmsClient.CanonicalItemState.TRANSIT))
				.thenReturn(rwc);
		}
		else {
			log.error("No patron system to update -- this is unlikely");
			return Mono.just(rwc);
		}	
	}

	public Mono<RequestWorkflowContext> updatePickupItem(RequestWorkflowContext rwc) {
		if  ( rwc.getPatronRequest().getPickupItemId() != null ) {
			log.warn("Pickup item ID is SET but pickup anywhere is not yet implemented. No action");
			return Mono.just(rwc);
		}
		else {
			log.debug("No PUA item to update");
			return Mono.just(rwc);
		}
	}
}
