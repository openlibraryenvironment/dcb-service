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
import io.micronaut.data.r2dbc.operations.R2dbcOperations;
import javax.transaction.Transactional;

/**
 * This class gathers together the code which detects that an object in a remote system has
 * changed state, and attempts to trigger the appropriate local workflow for dealing with that
 * scenario.
 */
// public class HostLmsReactions implements ApplicationEventListener<StateChange> {
// @Context
@Singleton
public class HostLmsReactions {

        private static final Logger log = LoggerFactory.getLogger(HostLmsReactions.class);
        private final ApplicationContext appContext;
        private final R2dbcOperations r2dbcOperations;

        public HostLmsReactions(ApplicationContext appContext,
                                R2dbcOperations r2dbcOperations) {
                this.appContext = appContext;
                this.r2dbcOperations = r2dbcOperations;
        }

        @javax.annotation.PostConstruct
        private void init() {
                log.info("HostLmsReactions::init");
        }

	@Transactional
        @EventListener
        public void onTrackingEvent(TrackingRecord trackingRecord) {
                log.debug("onTrackingEvent {}",trackingRecord);
                String handler = null;
                Map<String,Object> context = new HashMap();

                if ( trackingRecord.getTrackigRecordType().equals(StateChange.STATE_CHANGE_RECORD) ) {
                        StateChange sc = (StateChange) trackingRecord;
                        context.put("StateChange",sc);

                        // This will be replaced by a table in the near future
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


                // https://stackoverflow.com/questions/74183112/how-to-select-the-correct-transactionmanager-when-using-r2dbc-together-with-flyw
                if ( handler != null ) {
                        log.debug("Invoke action {}",handler);
                        WorkflowAction action = appContext.getBean(WorkflowAction.class, Qualifiers.byName(handler));
                        if ( action != null ) {
				log.debug("Invoke {}",action.getClass().getName());
				Mono.just(action.execute(context))
                                        .doOnNext(ctx -> log.debug("Action completed:"+ctx))
                                        .block();
                        }
                        else {
                                throw new RuntimeException("Missing qualified WorkflowAction for handler "+handler);
                        }
                }
        }
        
}

