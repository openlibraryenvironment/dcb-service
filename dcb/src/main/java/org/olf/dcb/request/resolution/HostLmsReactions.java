package org.olf.dcb.request.resolution;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import io.micronaut.context.event.ApplicationEventListener;
import jakarta.inject.Singleton;
import org.olf.dcb.tracking.model.TrackingRecord;
import org.olf.dcb.tracking.model.StateChange;
import io.micronaut.context.annotation.Context;
import io.micronaut.runtime.event.annotation.EventListener;
import io.micronaut.inject.qualifiers.Qualifiers;
import org.olf.dcb.request.workflow.WorkflowAction;
import io.micronaut.context.ApplicationContext;
import java.util.Map;
import java.util.HashMap;

/**
 * This class gathers together the code which detects that an object in a remote system has
 * changed state, and attempts to trigger the appropriate local workflow for dealing with that
 * scenario.
 */
// public class HostLmsReactions implements ApplicationEventListener<StateChange> {
@Context
public class HostLmsReactions {

        private static final Logger log = LoggerFactory.getLogger(HostLmsReactions.class);
        private final ApplicationContext appContext;

        public HostLmsReactions(ApplicationContext appContext) {
                this.appContext = appContext;
        }

        @javax.annotation.PostConstruct
        private void init() {
                log.info("HostLmsReactions::init");
        }

        @EventListener
        public void onTrackingEvent(TrackingRecord trackingRecord) {
                log.debug("onTrackingEvent {}",trackingRecord);
                String handler = null;
                Map<String,Object> context = new HashMap();

                if ( trackingRecord.getTrackigRecordType().equals(StateChange.STATE_CHANGE_RECORD) ) {
                        StateChange sc = (StateChange) trackingRecord;
                        context.put("StateChange",sc);
                        if ( sc.getToState().equals("TRANSIT") ) {
                          handler="SupplierRequestInTransit";
                        }
                        else if ( sc.getToState().equals("MISSING") ) {
                          handler="SupplierRequestMissing";
                        }
                        else {
                                log.warn("Unhandled ToState:{}",sc.getToState());
                        }
                }

                if ( handler != null ) {
                        log.debug("Invoke action {}",handler);
                        WorkflowAction action = appContext.getBean(WorkflowAction.class, Qualifiers.byName(handler));
                        if ( action != null ) {
                                Mono.just(action.execute(context))
                                        .block();
                        }
                        else {
                                throw new RuntimeException("Missing qualified WorkflowAction for handler "+handler);
                        }
                }
        }
        
}

