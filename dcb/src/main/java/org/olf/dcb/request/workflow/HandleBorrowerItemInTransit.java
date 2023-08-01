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
@Named("BorrowerRequestInTransit")
public class HandleBorrowerItemInTransit implements WorkflowAction {

        private static final Logger log = LoggerFactory.getLogger(HandleSupplierInTransit.class);
        private RequestWorkflowContextHelper requestWorkflowContextHelper;

        public HandleBorrowerItemInTransit(
                RequestWorkflowContextHelper requestWorkflowContextHelper) {
                this.requestWorkflowContextHelper = requestWorkflowContextHelper;
        }
        
        @Transactional
        public Mono<Map<String,Object>> execute(Map<String,Object> context) {
                StateChange sc = (StateChange) context.get("StateChange");
                log.debug("HandleBorrowerItemInTransit {}",sc);
                return Mono.just(context);
        }
}
