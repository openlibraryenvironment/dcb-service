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
        private RequestWorkflowContextHelper requestWorkflowContextHelper;
	private HostLmsService hostLmsService;

        public HandleSupplierInTransit(
                SupplierRequestRepository supplierRequestRepository,
		HostLmsService hostLmsService,
                RequestWorkflowContextHelper requestWorkflowContextHelper) {
                this.supplierRequestRepository = supplierRequestRepository;
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
                                .flatMap(rwc -> Mono.from(supplierRequestRepository.saveOrUpdate(sr)))
                                .doOnNext(ssr -> log.debug("Saved {}",ssr))
                                .thenReturn(context);
                }
                else {
			log.warn("Supplier request in context was null. Cannot save");
                        return Mono.just(context);
                }
        }

        // If there is a separate pickup location, the pickup location needs to be updated
        // If there is a separate patron request (There always will be EXCEPT for the "Local" case) update it
        public Mono updateUpstreamSystems(RequestWorkflowContext rwc) {
                log.debug("updateUpstreamSystems rwc={},{}",rwc.getPatronSystemCode(),rwc.getPatronRequest().getPickupRequestId());
		return hostLmsService.getClientFor(rwc.getPatronSystemCode())
		 	.flatMap(hostLmsClient -> hostLmsClient.updateRequestStatus(rwc.getPatronRequest().getLocalRequestId(), HostLmsClient.CanonicalRequestState.TRANSIT))
			.thenReturn(Mono.just(rwc));

		// rwc.getPatronRequest().getLocalRequestId() == The request placed at the patron home system to represent this loan
		// rwc.getPatronRequest().getPickupRequestId() == The request placed at a third party pickup location
        }

}
